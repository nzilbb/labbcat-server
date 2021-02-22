import { TestBed } from '@angular/core/testing';

import { LabbcatService } from './labbcat.service';

describe('LabbcatService', () => {
  let service: LabbcatService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LabbcatService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
