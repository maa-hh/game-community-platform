import { buildGameDetailPageUrl, buildSharePostUrl } from './shareUrl';

describe('shareUrl', () => {
  it('builds a post URL from the registered frontend route', () => {
    expect(buildSharePostUrl('post-public-id')).toBe(
      `${window.location.origin}/post/post-public-id`,
    );
  });

  it('builds a game URL from the registered frontend route', () => {
    expect(buildGameDetailPageUrl(620)).toBe(
      `${window.location.origin}/game/620`,
    );
  });
});
