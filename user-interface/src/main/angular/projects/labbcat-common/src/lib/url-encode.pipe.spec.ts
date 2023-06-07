import { UrlEncodePipe } from './url-encode.pipe';

describe('UrlEncodePipe', () => {
  it('create an instance', () => {
    const pipe = new UrlEncodePipe();
    expect(pipe).toBeTruthy();
  });
});
