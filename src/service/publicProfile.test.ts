import hyRequest from '@/service/request';
import {
  fetchUserSteamLibraryByAccountApi,
  fetchUserSteamProfileByAccountApi,
} from '@/service/steam';
import { getUserInfo, hasUsableAuthSession } from '@/utils/storage';

jest.mock('@/service/request', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

jest.mock('@/utils/storage', () => ({
  getUserInfo: jest.fn(),
  hasUsableAuthSession: jest.fn(),
}));

const requestGet = hyRequest.get as jest.Mock;
const mockGetUserInfo = getUserInfo as jest.MockedFunction<typeof getUserInfo>;
const mockHasUsableAuthSession = hasUsableAuthSession as jest.MockedFunction<
  typeof hasUsableAuthSession
>;
let fetchProfileSocialStatsByAccountApi: (typeof import('@/service/social'))['fetchProfileSocialStatsByAccountApi'];

describe('public profile services', () => {
  beforeAll(async () => {
    Object.defineProperty(globalThis, 'structuredClone', {
      configurable: true,
      value: <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T,
    });
    ({ fetchProfileSocialStatsByAccountApi } =
      await import('@/service/social'));
  });

  beforeEach(() => {
    requestGet.mockReset();
    mockGetUserInfo.mockReturnValue(null);
    mockHasUsableAuthSession.mockReturnValue(false);
  });

  it('loads public follow counts for a guest instead of returning zero early', async () => {
    requestGet.mockResolvedValue({
      code: 200,
      data: { following: 12, fans: 34 },
    });

    await expect(fetchProfileSocialStatsByAccountApi(10000)).resolves.toEqual({
      following: 12,
      followers: 34,
      likes: 0,
      favorites: 0,
    });
    expect(requestGet).toHaveBeenCalledWith({
      url: '/social/follow/count/by-account/10000',
      skipAuth: true,
    });
  });

  it('marks another users Steam profile and library as guest-readable', () => {
    fetchUserSteamProfileByAccountApi(10000);
    fetchUserSteamLibraryByAccountApi(10000);

    expect(requestGet).toHaveBeenNthCalledWith(1, {
      url: '/steam/users/by-account/10000/profile',
      skipAuth: true,
    });
    expect(requestGet).toHaveBeenNthCalledWith(2, {
      url: '/steam/users/by-account/10000/library',
      skipAuth: true,
    });
  });
});
