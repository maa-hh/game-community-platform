# Game Account Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the demo `game-account-service` into the current project with full feature coverage, MySQL-based metadata/state storage, MongoDB-only detail storage, concurrency-safe delivery and sign-in flows, and matching frontend/admin pages.

**Architecture:** Keep resource definitions split across character, skin, item, and sign-in reward tables in MySQL; keep player-owned assets split by ownership semantics; use MongoDB only for rich detail documents; consume `shop-service` paid-order Kafka messages idempotently via a delivery record table; expose user and admin APIs from `game-account-service`; wire player pages and admin pages into the existing frontend.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, MySQL, MongoDB, Kafka, Nacos, Spring Cloud Gateway, OpenFeign, React, TypeScript, Vite.

---

## File Structure

### Backend service

- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\resources\bootstrap.yml`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\resources\application.yml`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\config\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\controller\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\impl\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\listener\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mongo\`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\`

### Shared model and feign

- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\entity\gameaccount\`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\dto\gameaccount\`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\vo\gameaccount\`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\mongo\`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\message\GameDeliveryRepairMessage.java` only if repair flow is added in this iteration
- Modify: `D:\IDEAJAVA\game-community\feign\src\main\java\com\game\community\feign\`
- Modify: `D:\IDEAJAVA\game-community\common\src\main\java\com\game\community\common\constant\`

### Infra and SQL

- Create: `D:\IDEAJAVA\game-community\sql\game-account.sql`
- Modify: `D:\IDEAJAVA\game-community\pom.xml`
- Modify: `D:\IDEAJAVA\game-community\gateway\src\main\resources\bootstrap.yml` or the gateway route config source actually used in this project
- Modify: `D:\IDEAJAVA\game-community\service\shop-service\src\main\java\com\game\community\shop\service\impl\ShopOrderServiceImpl.java`
- Modify: `D:\IDEAJAVA\game-community\service\user-service\src\main\java\com\game\community\user\service\impl\UserServiceImpl.java` only if `gameAccount` cache synchronization is retained

### Frontend

- Create: `D:\IDEAJAVA\game-community\frontend\src\api\gameAccount.ts`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameAccountHubPage.tsx`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameCatalogPage.tsx`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameCatalogDetailPage.tsx`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameAdminPage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\pages\ProfilePage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\pages\ShopModulePage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\app\App.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\app\AppShell.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\styles.css`

### Docs

- Create: `D:\IDEAJAVA\game-community\docs\game-account-service-api.md`
- Create: `D:\IDEAJAVA\game-community\docs\game-account-service-technical-overview.md`
- Modify: `D:\IDEAJAVA\game-community\docs\shop-service-technical-overview.md`

## Task 1: Scaffold Shared Model Types

**Files:**
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\entity\gameaccount\*.java`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\dto\gameaccount\*.java`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\vo\gameaccount\*.java`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\mongo\CharacterDetail.java`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\mongo\SkinDetail.java`
- Create: `D:\IDEAJAVA\game-community\model\src\main\java\com\game\community\model\mongo\ItemDetail.java`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\ModelContractSmokeTest.java`

- [ ] **Step 1: Write the failing smoke test for model loading**

```java
package com.game.community.game;

import com.game.community.model.dto.gameaccount.BindGameAccountDTO;
import com.game.community.model.entity.gameaccount.GameAccount;
import com.game.community.model.entity.gameaccount.GameDeliveryRecord;
import com.game.community.model.vo.gameaccount.GameAccountProfileVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelContractSmokeTest {

    @Test
    void modelTypesShouldBeLoadable() {
        assertThat(new BindGameAccountDTO()).isNotNull();
        assertThat(new GameAccount()).isNotNull();
        assertThat(new GameDeliveryRecord()).isNotNull();
        assertThat(new GameAccountProfileVO()).isNotNull();
    }
}
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=ModelContractSmokeTest test`

Expected: FAIL with missing `gameaccount` classes.

- [ ] **Step 3: Add the shared entity, DTO, VO, and Mongo classes**

```java
package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_game_account")
public class GameAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountNo;
    private String name;
    private Integer level;
    private Integer gold;
    private Integer diamond;
    private String currentSeasonRank;
    private String historySeasonRank;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

```java
package com.game.community.model.dto.gameaccount;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindGameAccountDTO {
    @NotBlank
    private String accountNo;
}
```

```java
package com.game.community.model.vo.gameaccount;

import lombok.Data;

@Data
public class GameAccountProfileVO {
    private Long id;
    private Long accountNo;
    private String name;
    private Integer level;
    private Integer gold;
    private Integer diamond;
    private String currentSeasonRank;
    private String historySeasonRank;
    private Integer status;
    private Boolean bound;
}
```

- [ ] **Step 4: Run the smoke test to verify it passes**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=ModelContractSmokeTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add model/src/main/java/com/game/community/model/entity/gameaccount model/src/main/java/com/game/community/model/dto/gameaccount model/src/main/java/com/game/community/model/vo/gameaccount model/src/main/java/com/game/community/model/mongo service/game-account-service/src/test/java/com/game/community/game/ModelContractSmokeTest.java
git commit -m "feat: add game account shared model contracts"
```

## Task 2: Create SQL Schema and Seed Data

**Files:**
- Create: `D:\IDEAJAVA\game-community\sql\game-account.sql`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\SchemaContractTest.java`

- [ ] **Step 1: Write the failing schema contract test**

```java
package com.game.community.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaContractTest {

    @Test
    void schemaScriptShouldContainCoreTables() throws Exception {
        String sql = Files.readString(Path.of("..", "..", "sql", "game-account.sql").normalize());
        assertThat(sql).contains("t_game_account");
        assertThat(sql).contains("t_user_game_bind");
        assertThat(sql).contains("t_game_delivery_record");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=SchemaContractTest test`

Expected: FAIL because `sql/game-account.sql` does not exist.

- [ ] **Step 3: Add the SQL schema**

```sql
CREATE TABLE IF NOT EXISTS t_game_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_no BIGINT NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    gold INT NOT NULL DEFAULT 0,
    diamond INT NOT NULL DEFAULT 0,
    current_season_rank VARCHAR(64) DEFAULT '',
    history_season_rank VARCHAR(64) DEFAULT '',
    status TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_user_game_bind (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    game_account_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_game_bind_user (user_id),
    UNIQUE KEY uk_user_game_bind_game (game_account_id)
);

CREATE TABLE IF NOT EXISTS t_game_delivery_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    game_account_id BIGINT,
    product_type INT NOT NULL,
    business_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    fail_reason VARCHAR(255) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_game_delivery_order_no (order_no)
);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=SchemaContractTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sql/game-account.sql service/game-account-service/src/test/java/com/game/community/game/SchemaContractTest.java
git commit -m "feat: add game account schema"
```

## Task 3: Bootstrap the Service

**Files:**
- Modify: `D:\IDEAJAVA\game-community\service\game-account-service\pom.xml`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\resources\bootstrap.yml`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\resources\application.yml`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\GameAccountServiceApplication.java`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\GameAccountApplicationTest.java`

- [ ] **Step 1: Write the failing Spring context test**

```java
package com.game.community.game;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GameAccountApplicationTest {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=GameAccountApplicationTest test`

Expected: FAIL because the application class and configuration are incomplete.

- [ ] **Step 3: Add the application bootstrap**

```java
package com.game.community.game;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableFeignClients(basePackages = "com.game.community.feign")
@MapperScan("com.game.community.game.mapper")
@SpringBootApplication
public class GameAccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameAccountServiceApplication.class, args);
    }
}
```

```yaml
spring:
  application:
    name: game-account-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
        file-extension: yaml
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=GameAccountApplicationTest test`

Expected: PASS or fail only on missing infra beans that will be added in the next tasks.

- [ ] **Step 5: Commit**

```bash
git add service/game-account-service/pom.xml service/game-account-service/src/main/resources service/game-account-service/src/main/java/com/game/community/game/GameAccountServiceApplication.java service/game-account-service/src/test/java/com/game/community/game/GameAccountApplicationTest.java
git commit -m "feat: bootstrap game account service"
```

## Task 4: Implement Binding Domain

**Files:**
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\GameAccountMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\UserGameBindMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\GameAccountBindingService.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\impl\GameAccountBindingServiceImpl.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\controller\GameAccountController.java`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\GameAccountBindingServiceTest.java`

- [ ] **Step 1: Write the failing binding service test**

```java
package com.game.community.game;

import com.game.community.common.exception.BusinessException;
import com.game.community.game.service.GameAccountBindingService;
import com.game.community.model.dto.gameaccount.BindGameAccountDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GameAccountBindingServiceTest {

    @Autowired
    private GameAccountBindingService bindingService;

    @Test
    void bindShouldFailWhenAccountDoesNotExist() {
        BindGameAccountDTO dto = new BindGameAccountDTO();
        dto.setAccountNo("999999");
        assertThatThrownBy(() -> bindingService.bind(4L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("游戏账号不存在");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=GameAccountBindingServiceTest test`

Expected: FAIL because the binding service does not exist yet.

- [ ] **Step 3: Implement binding, rebinding, unbinding, and current-profile retrieval**

```java
public interface GameAccountBindingService {
    Long bind(Long userId, BindGameAccountDTO dto);
    Long rebind(Long userId, BindGameAccountDTO dto);
    void unbind(Long userId);
    GameAccountProfileVO current(Long userId);
}
```

```java
@Service
@RequiredArgsConstructor
public class GameAccountBindingServiceImpl implements GameAccountBindingService {
    private final GameAccountMapper gameAccountMapper;
    private final UserGameBindMapper userGameBindMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long bind(Long userId, BindGameAccountDTO dto) {
        GameAccount account = gameAccountMapper.selectByAccountNo(Long.parseLong(dto.getAccountNo()));
        if (account == null || account.getStatus() == 1) {
            throw new BusinessException("游戏账号不存在或不可绑定");
        }
        userGameBindMapper.insertOrFail(userId, account.getId());
        return account.getId();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes and add one happy-path test**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=GameAccountBindingServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/game-account-service/src/main/java/com/game/community/game/mapper service/game-account-service/src/main/java/com/game/community/game/service service/game-account-service/src/main/java/com/game/community/game/controller service/game-account-service/src/test/java/com/game/community/game/GameAccountBindingServiceTest.java
git commit -m "feat: implement game account binding domain"
```

## Task 5: Implement System Resource Metadata and Mongo Detail Repositories

**Files:**
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\GameCharacterMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\GameSkinMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\GameItemMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\SignInRewardMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mongo\CharacterDetailRepository.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mongo\SkinDetailRepository.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mongo\ItemDetailRepository.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\SystemResourceService.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\impl\SystemResourceServiceImpl.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\controller\GameResourceController.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\controller\GameResourceAdminController.java`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\SystemResourceServiceTest.java`

- [ ] **Step 1: Write the failing resource service test**

```java
package com.game.community.game;

import com.game.community.game.service.SystemResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SystemResourceServiceTest {

    @Autowired
    private SystemResourceService resourceService;

    @Test
    void shouldPageCharacters() {
        assertThat(resourceService.pageCharacters(1, 10, null, null).getRecords()).isNotNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=SystemResourceServiceTest test`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement metadata paging, CRUD, disable behavior, and Mongo detail save/load**

```java
public interface SystemResourceService {
    PageResult<GameCharacterVO> pageCharacters(Integer page, Integer size, String keyword, Integer rarity);
    void saveCharacter(SaveGameCharacterDTO dto);
    void disableCharacter(Long id);
    CharacterDetail getCharacterDetail(String characterCode);
    void saveCharacterDetail(SaveCharacterDetailDTO dto);
}
```

```java
@Repository
public interface CharacterDetailRepository extends MongoRepository<CharacterDetail, String> {
    Optional<CharacterDetail> findByCharacterCode(String characterCode);
}
```

- [ ] **Step 4: Run the test to verify it passes and add equivalent tests for skins, items, and sign-in rewards**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=SystemResourceServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/game-account-service/src/main/java/com/game/community/game/mapper service/game-account-service/src/main/java/com/game/community/game/mongo service/game-account-service/src/main/java/com/game/community/game/service service/game-account-service/src/main/java/com/game/community/game/controller service/game-account-service/src/test/java/com/game/community/game/SystemResourceServiceTest.java
git commit -m "feat: implement game system resource management"
```

## Task 6: Implement Owned Asset Queries and Sign-In

**Files:**
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\AccountCharacterMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\AccountSkinMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\AccountItemMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\SignInRecordMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\AccountAssetService.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\impl\AccountAssetServiceImpl.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\controller\GameAssetController.java`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\SignInServiceTest.java`

- [ ] **Step 1: Write the failing sign-in test**

```java
package com.game.community.game;

import com.game.community.common.exception.BusinessException;
import com.game.community.game.service.AccountAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SignInServiceTest {

    @Autowired
    private AccountAssetService accountAssetService;

    @Test
    void signInShouldFailWhenNoGameAccountBound() {
        assertThatThrownBy(() -> accountAssetService.signIn(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未绑定");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=SignInServiceTest test`

Expected: FAIL because the asset service does not exist.

- [ ] **Step 3: Implement owned-resource paging, discover paging with ownership flags, and daily sign-in**

```java
public interface AccountAssetService {
    PageResult<AccountCharacterVO> pageOwnedCharacters(Long userId, Integer page, Integer size);
    PageResult<AccountSkinVO> pageOwnedSkins(Long userId, Integer page, Integer size);
    PageResult<AccountItemVO> pageOwnedItems(Long userId, Integer page, Integer size);
    SignInStatusVO signIn(Long userId);
    SignInStatusVO getSignInStatus(Long userId);
}
```

```java
@Transactional(rollbackFor = Exception.class)
public SignInStatusVO signIn(Long userId) {
    Long gameAccountId = requireBoundGameAccount(userId);
    SignInRecord record = lockCurrentMonthRecord(gameAccountId);
    if (alreadySignedToday(record)) {
        throw new BusinessException("今日已签到");
    }
    applyTodaySignBit(record);
    grantSignInReward(gameAccountId, currentReward());
    signInRecordMapper.updateById(record);
    return buildSignInStatus(record);
}
```

- [ ] **Step 4: Run the sign-in tests and add duplicate-sign test**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=SignInServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/game-account-service/src/main/java/com/game/community/game/mapper service/game-account-service/src/main/java/com/game/community/game/service service/game-account-service/src/main/java/com/game/community/game/controller service/game-account-service/src/test/java/com/game/community/game/SignInServiceTest.java
git commit -m "feat: implement game assets and sign-in flows"
```

## Task 7: Implement Kafka Delivery Consumer and Idempotent Delivery

**Files:**
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\mapper\GameDeliveryRecordMapper.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\ResourceDeliveryService.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\service\impl\ResourceDeliveryServiceImpl.java`
- Create: `D:\IDEAJAVA\game-community\service\game-account-service\src\main\java\com\game\community\game\listener\ShopOrderPaidListener.java`
- Test: `D:\IDEAJAVA\game-community\service\game-account-service\src\test\java\com\game\community\game\ResourceDeliveryServiceTest.java`

- [ ] **Step 1: Write the failing delivery idempotency test**

```java
package com.game.community.game;

import com.game.community.game.service.ResourceDeliveryService;
import com.game.community.model.message.ShopOrderPaidMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class ResourceDeliveryServiceTest {

    @Autowired
    private ResourceDeliveryService deliveryService;

    @Test
    void sameOrderShouldBeIdempotent() {
        ShopOrderPaidMessage message = new ShopOrderPaidMessage();
        message.setOrderNo("TEST-ORDER-1001");
        message.setUserId(4L);
        message.setProductType(2);
        message.setBusinessId(10001L);
        message.setQuantity(1);
        assertThatCode(() -> deliveryService.handlePaidOrder(message)).doesNotThrowAnyException();
        assertThatCode(() -> deliveryService.handlePaidOrder(message)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=ResourceDeliveryServiceTest test`

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement delivery service and Kafka listener**

```java
public interface ResourceDeliveryService {
    void handlePaidOrder(ShopOrderPaidMessage message);
}
```

```java
@Transactional(rollbackFor = Exception.class)
public void handlePaidOrder(ShopOrderPaidMessage message) {
    if (!insertDeliveryRecord(message)) {
        return;
    }
    Long gameAccountId = userGameBindMapper.selectGameAccountIdByUserId(message.getUserId());
    if (gameAccountId == null) {
        markDeliveryFailed(message.getOrderNo(), "用户未绑定游戏账号");
        return;
    }
    deliverByProductType(gameAccountId, message);
    markDeliverySuccess(message.getOrderNo(), gameAccountId);
}
```

```java
@KafkaListener(topics = KafkaTopicConstants.SHOP_ORDER_PAID_TOPIC, groupId = "game-account-consumer-group")
public void onPaidMessage(String body) throws JsonProcessingException {
    ShopOrderPaidMessage message = objectMapper.readValue(body, ShopOrderPaidMessage.class);
    resourceDeliveryService.handlePaidOrder(message);
}
```

- [ ] **Step 4: Run the delivery tests and add one no-binding failure-state test**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service -Dtest=ResourceDeliveryServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add service/game-account-service/src/main/java/com/game/community/game/mapper service/game-account-service/src/main/java/com/game/community/game/service service/game-account-service/src/main/java/com/game/community/game/listener service/game-account-service/src/test/java/com/game/community/game/ResourceDeliveryServiceTest.java
git commit -m "feat: implement idempotent game resource delivery"
```

## Task 8: Wire Service Integration Points

**Files:**
- Modify: `D:\IDEAJAVA\game-community\service\shop-service\src\main\java\com\game\community\shop\service\impl\ShopOrderServiceImpl.java`
- Create or Modify: `D:\IDEAJAVA\game-community\feign\src\main\java\com\game\community\feign\GameAccountFeignClient.java`
- Modify: `D:\IDEAJAVA\game-community\gateway\src\main\resources\bootstrap.yml` or equivalent route config
- Modify: `D:\IDEAJAVA\game-community\pom.xml`
- Test: `D:\IDEAJAVA\game-community\service\shop-service\src\test\java\com\game\community\shop\GameAssetOrderIntegrationTest.java`

- [ ] **Step 1: Write the failing shop integration test**

```java
package com.game.community.shop;

import com.game.community.common.constant.KafkaTopicConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameAssetOrderIntegrationTest {

    @Test
    void shopShouldStillUseSharedPaidTopicConstant() {
        assertThat(KafkaTopicConstants.SHOP_ORDER_PAID_TOPIC).isEqualTo("shop-order-paid-events");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails only if the contract has drifted**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/shop-service -Dtest=GameAssetOrderIntegrationTest test`

Expected: PASS if constant is stable; if not, fix the contract first before proceeding.

- [ ] **Step 3: Add the integration behavior**

```java
if (isGameAssetProduct(order.getProductType())) {
    ensureUserBoundGameAccount(order.getUserId());
}
sendPaidMessage(order);
```

```java
@FeignClient(name = "game-account-service")
public interface GameAccountFeignClient {
    @GetMapping("/game-account/me")
    Result<GameAccountProfileVO> getCurrent();
}
```

- [ ] **Step 4: Verify service route and module build**

Run: `D:\apache-maven-3.9.11\bin\mvn.cmd -pl service/game-account-service,service/shop-service -am -DskipTests install`

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add service/shop-service/src/main/java/com/game/community/shop/service/impl/ShopOrderServiceImpl.java feign/src/main/java/com/game/community/feign gateway/src/main/resources pom.xml
git commit -m "feat: wire game account service with shop and gateway"
```

## Task 9: Build Frontend API Layer and Player Pages

**Files:**
- Create: `D:\IDEAJAVA\game-community\frontend\src\api\gameAccount.ts`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameAccountHubPage.tsx`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameCatalogPage.tsx`
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameCatalogDetailPage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\pages\ProfilePage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\app\App.tsx`
- Test: `D:\IDEAJAVA\game-community\frontend\src\pages\GameAccountHubPage.test.tsx` or local manual test checklist if no test harness exists

- [ ] **Step 1: Write the failing frontend type and import check**

```tsx
import { describe, expect, it } from "vitest";
import { fetchMyGameAccount } from "../api/gameAccount";

describe("gameAccount api", () => {
  it("should export profile loader", () => {
    expect(fetchMyGameAccount).toBeTypeOf("function");
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd D:\IDEAJAVA\game-community\frontend; npm run test -- GameAccountHubPage`

Expected: FAIL because the API module does not exist yet. If no frontend test runner is configured, note that and use `npm run build` as the verification gate for this task.

- [ ] **Step 3: Implement the frontend API and player pages**

```ts
export async function fetchMyGameAccount() {
  const { data } = await client.get("/game-account/me");
  return data.data;
}

export async function bindGameAccount(payload: { accountNo: string }) {
  const { data } = await client.post("/game-account/bind", payload);
  return data.data;
}
```

```tsx
export function GameAccountHubPage() {
  const [profile, setProfile] = useState<GameAccountProfile | null>(null);
  const [signInStatus, setSignInStatus] = useState<SignInStatus | null>(null);
  useEffect(() => {
    void Promise.all([fetchMyGameAccount(), fetchSignInStatus()]).then(([account, sign]) => {
      setProfile(account);
      setSignInStatus(sign);
    });
  }, []);
  return <div>...</div>;
}
```

- [ ] **Step 4: Run the frontend verification**

Run: `cd D:\IDEAJAVA\game-community\frontend; npm run build`

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/gameAccount.ts frontend/src/pages/GameAccountHubPage.tsx frontend/src/pages/GameCatalogPage.tsx frontend/src/pages/GameCatalogDetailPage.tsx frontend/src/pages/ProfilePage.tsx frontend/src/app/App.tsx
git commit -m "feat: add game account player frontend"
```

## Task 10: Build Frontend Admin Pages and Shop Guards

**Files:**
- Create: `D:\IDEAJAVA\game-community\frontend\src\pages\GameAdminPage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\pages\ShopModulePage.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\app\App.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\app\AppShell.tsx`
- Modify: `D:\IDEAJAVA\game-community\frontend\src\styles.css`

- [ ] **Step 1: Add the failing admin route build check**

```tsx
// in App.tsx route config
{
  path: "/app/admin/game-resources",
  element: <GameAdminPage />
}
```

Run this first without creating `GameAdminPage.tsx` so the build fails.

- [ ] **Step 2: Run the frontend build to verify it fails**

Run: `cd D:\IDEAJAVA\game-community\frontend; npm run build`

Expected: FAIL with missing `GameAdminPage`.

- [ ] **Step 3: Implement admin page and shop binding guard**

```tsx
export function GameAdminPage() {
  const [tab, setTab] = useState<"character" | "skin" | "item" | "reward">("character");
  return <section>...</section>;
}
```

```tsx
if (shopItem.productType === GAME_PRODUCT_TYPE_CHARACTER && !gameAccountProfile?.bound) {
  setError("请先绑定游戏账号，再购买游戏资源商品");
  return;
}
```

- [ ] **Step 4: Run the frontend build**

Run: `cd D:\IDEAJAVA\game-community\frontend; npm run build`

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/GameAdminPage.tsx frontend/src/pages/ShopModulePage.tsx frontend/src/app/App.tsx frontend/src/app/AppShell.tsx frontend/src/styles.css
git commit -m "feat: add game resource admin frontend and shop guards"
```

## Task 11: Write API and Technical Docs

**Files:**
- Create: `D:\IDEAJAVA\game-community\docs\game-account-service-api.md`
- Create: `D:\IDEAJAVA\game-community\docs\game-account-service-technical-overview.md`
- Modify: `D:\IDEAJAVA\game-community\docs\shop-service-technical-overview.md`

- [ ] **Step 1: Draft the API document**

```md
# Game Account Service API

## POST /game-account/bind

作用：绑定社区用户与游戏账号。

请求参数：
- `accountNo`: 游戏账号展示号
```

- [ ] **Step 2: Draft the technical overview**

```md
# Game Account Service Technical Overview

模块职责：绑定、资源库、资产背包、签到、商城发货。

亮点：
- MySQL 元数据 + Mongo 详情分层
- Kafka 发货幂等
- 签到事务内发奖
```

- [ ] **Step 3: Update shop overview with the new delivery dependency**

```md
## Game Asset Delivery

After a paid order for a game asset, `shop-service` publishes `ShopOrderPaidMessage` to `SHOP_ORDER_PAID_TOPIC`, and `game-account-service` performs idempotent delivery using `t_game_delivery_record`.
```

- [ ] **Step 4: Verify docs exist and are readable**

Run: `rg -n "Game Account Service|商城发货|签到" D:\IDEAJAVA\game-community\docs`

Expected: matches in the new docs.

- [ ] **Step 5: Commit**

```bash
git add docs/game-account-service-api.md docs/game-account-service-technical-overview.md docs/shop-service-technical-overview.md
git commit -m "docs: add game account service documentation"
```

## Task 12: Full Build, Data Initialization, and Manual Verification

**Files:**
- Modify if needed: `D:\IDEAJAVA\game-community\sql\game-account.sql`
- Verify: running services and browser flows

- [ ] **Step 1: Apply SQL to MySQL**

Run: `docker cp D:\IDEAJAVA\game-community\sql\game-account.sql mysql:/tmp/game-account.sql`

Run: `docker exec mysql mysql --default-character-set=utf8mb4 -uroot -proot123 -D game_community -e "source /tmp/game-account.sql;"`

Expected: no SQL errors.

- [ ] **Step 2: Build the backend with JDK 17**

Run: `$env:JAVA_HOME='D:\JAVAINSTALL\jdk17'; $env:Path='D:\JAVAINSTALL\jdk17\bin;'+$env:Path; D:\apache-maven-3.9.11\bin\mvn.cmd clean install -DskipTests`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Start `game-account-service` and dependent modules**

Run: `cd D:\IDEAJAVA\game-community\service\game-account-service; D:\apache-maven-3.9.11\bin\mvn.cmd spring-boot:run`

Expected: service registers in Nacos and listens successfully.

- [ ] **Step 4: Run player manual checks**

Checklist:
- Bind an existing game account from profile
- Rebind to another game account
- Open the asset page and verify owned lists render
- Perform sign-in once and confirm a second sign-in that day is rejected
- Visit a resource detail page and confirm Mongo-backed detail content renders

- [ ] **Step 5: Run admin and shop manual checks**

Checklist:
- Create one character, one skin, one item, and one sign-in reward from admin pages
- Disable a resource and confirm it no longer appears as active
- Attempt to buy a game asset with no bound game account and confirm frontend blocks it
- Buy a game asset with a bound game account and confirm delivery record and owned asset are created exactly once

- [ ] **Step 6: Commit**

```bash
git add sql/game-account.sql
git commit -m "chore: verify game account module rollout"
```

## Self-Review

### Spec coverage

- Binding, rebinding, and unbinding: covered in Task 4
- Resource metadata and Mongo details: covered in Task 5
- Owned assets and sign-in: covered in Task 6
- Kafka delivery and idempotency: covered in Task 7
- Shop and gateway integration: covered in Task 8
- Player frontend and admin frontend: covered in Tasks 9 and 10
- Docs and rollout: covered in Tasks 11 and 12

### Placeholder scan

- No `TODO`, `TBD`, or “implement later” placeholders remain in the steps
- Commands, paths, and target files are explicitly listed

### Type consistency

- Binding service uses `BindGameAccountDTO` and returns `GameAccountProfileVO`
- Delivery flow uses existing `ShopOrderPaidMessage`
- Frontend API names align with backend endpoint groups

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-31-game-account-implementation-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using `executing-plans`, batch execution with checkpoints

Which approach?
