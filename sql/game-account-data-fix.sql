USE game_community;

UPDATE t_game_character
SET name = '刃行者', title = '裂风先锋'
WHERE character_code = 'char_blade';

UPDATE t_game_character
SET name = '星谕', title = '月海观测者'
WHERE character_code = 'char_oracle';

UPDATE t_game_character
SET name = '玄垒', title = '黑曜壁垒'
WHERE character_code = 'char_vanguard';

UPDATE t_game_skin
SET name = '夜巡刃影'
WHERE skin_code = 'skin_blade_night';

UPDATE t_game_skin
SET name = '潮声星梦'
WHERE skin_code = 'skin_oracle_tide';

UPDATE t_game_skin
SET name = '熔核壁垒'
WHERE skin_code = 'skin_vanguard_core';

UPDATE t_game_item
SET name = '金币补给箱'
WHERE item_code = 'item_gold_pack';

UPDATE t_game_item
SET name = '改名凭证'
WHERE item_code = 'item_rename_card';

UPDATE t_game_item
SET name = '皮肤体验券'
WHERE item_code = 'item_skin_ticket';

UPDATE t_sign_in_reward
SET reward_name = '金币 x100'
WHERE day_index = 1;

UPDATE t_sign_in_reward
SET reward_name = '金币 x200'
WHERE day_index = 2;

UPDATE t_sign_in_reward
SET reward_name = '皮肤体验券 x1'
WHERE day_index = 3;

UPDATE t_sign_in_reward
SET reward_name = '钻石 x20'
WHERE day_index = 4;

UPDATE t_sign_in_reward
SET reward_name = '金币 x500'
WHERE day_index = 5;

UPDATE t_sign_in_reward
SET reward_name = '改名凭证 x1'
WHERE day_index = 6;

UPDATE t_sign_in_reward
SET reward_name = '夜巡刃影 x1'
WHERE day_index = 7;
