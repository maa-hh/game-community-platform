import type { FeedItemData, MainTabKey, ProfileStats } from '@/types/profile';

/** 本地演示数据（统计 / 关注列表 mock） */
export const MOCK_STATS: ProfileStats = {
  following: 0,
  followers: 0,
  likes: 0,
  favorites: 0,
};
export const MOCK_FOLLOW_USERS = [
  {
    accountId: 100201,
    username: '攻略达人',
    avatar: 'https://picsum.photos/seed/u1/96/96',
    signature: '专注单机攻略',
  },
  {
    accountId: 100202,
    username: '肝帝',
    avatar: 'https://picsum.photos/seed/u2/96/96',
    signature: '每晚联机',
  },
];

export const MOCK_FAN_USERS = [
  {
    accountId: 100301,
    username: '萌新一号',
    avatar: 'https://picsum.photos/seed/c1/64/64',
    signature: '求带飞',
  },
  {
    accountId: 100302,
    username: '长文爱好者',
    avatar: 'https://picsum.photos/seed/u3/96/96',
    signature: '爱看深度评测',
  },
];

const images = {
  wide: 'https://picsum.photos/seed/gc1/640/360',
  tall: 'https://picsum.photos/seed/gc2/360/480',
  square: 'https://picsum.photos/seed/gc3/400/400',
  landscape: 'https://picsum.photos/seed/gc4/520/300',
  poster: 'https://picsum.photos/seed/gc5/300/420',
  gameIcon: 'https://picsum.photos/seed/gameicon/64/64',
  tagIcon: 'https://picsum.photos/seed/tagicon/64/64',
};

const defaultAuthor = {
  accountId: 100001,
  nickname: '盒友玩家',
  avatar: 'https://picsum.photos/seed/me/96/96',
};

export const MOCK_FEEDS: Record<MainTabKey, FeedItemData[]> = {
  posts: [
    {
      id: 'p1',
      author: defaultAuthor,
      postType: 'image_text',
      title: '【喜加一】愿望单破 10W！发售倒计时 3 天！',
      content:
        '这是一款以经营为核心的模拟游戏，画面可爱、节奏轻松。整理了影之国前中期的运营路线和武器推荐，适合第一次上手的朋友。Boss 战注意带上抗性道具，开荒少走弯路，周末也能轻松通关核心内容。',
      images: [
        images.wide,
        images.tall,
        images.square,
        images.landscape,
        images.poster,
      ],
      createdAt: '2026-07-18 21:30',
      viewCount: 12840,
      likeCount: 13,
      commentCount: 21,
      liked: true,
      status: 'published',
      tags: [
        {
          text: '奶茶店模拟器 - 重生之我在冰堡甜城当店',
          icon: images.gameIcon,
        },
        { text: '喜加一' },
      ],
    },
    {
      id: 'p2',
      author: defaultAuthor,
      postType: 'article',
      title: '周末联机征集：双人成行 / 只狼随便',
      content:
        '晚上 8 点后有空，语音开黑，萌新老手都欢迎，求带也行。最好能稳定两小时以上，有麦克风优先。',
      images: [images.landscape, images.wide, images.tall],
      createdAt: '2026-07-16 14:05',
      viewCount: 2103,
      likeCount: 88,
      commentCount: 42,
      liked: false,
      status: 'published',
      tags: [{ text: '组队大厅' }, { text: '联机', icon: images.tagIcon }],
    },
    {
      id: 'p3',
      author: defaultAuthor,
      postType: 'video',
      title: '（草稿）本周游戏折扣清单还没写完',
      content:
        'Steam 特惠里挑了几款独立游戏，截图和价格对比还待补充。想把性价比高的先排前面，再补上史低标记。',
      coverUrl: images.landscape,
      createdAt: '2026-07-15 09:12',
      likeCount: 0,
      commentCount: 0,
      liked: false,
      status: 'draft',
      tags: [{ text: '草稿' }, { text: '视频' }],
    },
    {
      id: 'p4',
      author: defaultAuthor,
      postType: 'image_text',
      title: '社区活动投稿：我的桌面改造',
      content:
        '灯带、显示器支架和键鼠垫布置，审核通过后才会公开。这次主要想把线材收干净，顺便换了深色系桌面。',
      images: [images.square, images.poster, images.wide],
      createdAt: '2026-07-12 18:40',
      likeCount: 0,
      commentCount: 0,
      liked: false,
      status: 'draft',
      tags: [{ text: '数码桌搭', icon: images.tagIcon }],
    },
  ],
  history: [
    {
      id: 'h1',
      author: {
        accountId: 100001,
        nickname: '攻略达人',
        avatar: 'https://picsum.photos/seed/u1/96/96',
      },
      postType: 'article',
      title: '黑神话：悟空 全地图收集路线',
      content:
        '你浏览过这篇攻略，包含隐藏道具与支线触发条件。适合想一次补全收集的玩家，按章节拆分更清晰。',
      images: [images.wide, images.landscape, images.tall, images.square],
      createdAt: '昨天 20:18',
      viewCount: 56021,
      likeCount: 420,
      commentCount: 96,
      liked: false,
      tags: [{ text: '黑神话：悟空', icon: images.gameIcon }],
    },
    {
      id: 'h2',
      author: {
        accountId: 100001,
        nickname: '数据君',
        avatar: 'https://picsum.photos/seed/u2/96/96',
      },
      postType: 'image_text',
      title: 'CS2 新赛季段位分布统计',
      content: '看完段位分布后关掉了页面，可从这里快速回访。',
      createdAt: '昨天 11:02',
      viewCount: 9033,
      likeCount: 55,
      commentCount: 12,
      liked: false,
    },
  ],
  liked: [
    {
      id: 'l1',
      author: {
        accountId: 100001,
        nickname: '长评作者',
        avatar: 'https://picsum.photos/seed/u3/96/96',
      },
      postType: 'article',
      title: '为什么我认为独立游戏更值得推荐',
      content:
        '你赞过这篇长评，讨论了叙事节奏与玩法创新。作者从几个代表作切入，读起来很顺，评论区也很热闹。',
      images: [images.tall, images.square, images.wide],
      createdAt: '2026-07-10',
      likeCount: 2401,
      commentCount: 310,
      liked: true,
      tags: [{ text: '独立游戏' }, { text: '长评', icon: images.tagIcon }],
    },
  ],
  favorites: [
    {
      id: 'f1',
      author: {
        accountId: 100001,
        nickname: '硬件评测室',
        avatar: 'https://picsum.photos/seed/u4/96/96',
      },
      postType: 'image_text',
      title: '显示器选购避坑：高刷还是色彩？',
      content:
        '已加入收藏，方便对比参数再下单。文章把办公、电竞和创作场景分开讲，结论比较好落地。',
      images: [images.landscape],
      createdAt: '2026-07-08',
      likeCount: 180,
      commentCount: 44,
      liked: false,
      tags: [{ text: '数码', icon: images.tagIcon }],
    },
    {
      id: 'f2',
      author: {
        accountId: 100001,
        nickname: 'OST 精选',
        avatar: 'https://picsum.photos/seed/u5/96/96',
      },
      postType: 'video',
      title: '适合通勤听的游戏 OST 合集',
      content: '收藏于音乐专题，含 20 首精选。',
      coverUrl: images.wide,
      createdAt: '2026-07-01',
      likeCount: 66,
      commentCount: 8,
      liked: true,
      tags: [{ text: '音乐' }, { text: '视频' }],
    },
  ],
  comments: [
    {
      id: 'c1',
      targetArticleId: '1001',
      commentId: 'c1',
      author: defaultAuthor,
      postType: 'image_text',
      title: '评论',
      content:
        '个人最看好那款开放世界，美术风格太抓人了。如果优化做好，首发就冲。',
      createdAt: '2026-07-19 16:22',
      likeCount: 3,
      liked: false,
    },
    {
      id: 'c2',
      targetArticleId: '1002',
      commentId: 'c3',
      replyId: 'r-comment-1',
      author: defaultAuthor,
      postType: 'image_text',
      title: '回复',
      content:
        '有线模式下延迟确实稳很多，无线在 2.4G 下也够用，蓝牙场景就看距离了。',
      parentQuote: {
        nickname: '影像党',
        content: '小窗边看边评是真的香。',
      },
      createdAt: '2026-07-14 10:08',
      likeCount: 2,
      liked: true,
    },
  ],
  received: [
    {
      id: '1001',
      author: {
        accountId: 100001,
        nickname: '攻略达人',
        avatar: 'https://picsum.photos/seed/u1/96/96',
      },
      postType: 'article',
      title: '黑神话：悟空 全地图收集路线',
      content:
        '你浏览过这篇攻略，包含隐藏道具与支线触发条件。适合想一次补全收集的玩家，按章节拆分更清晰。',
      images: [images.wide, images.landscape, images.tall, images.square],
      createdAt: '昨天 18:20',
      viewCount: 56021,
      likeCount: 420,
      commentCount: 96,
      liked: false,
      tags: [{ text: '黑神话：悟空', icon: images.gameIcon }],
    },
  ],
};
