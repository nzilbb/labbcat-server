import { TestBed } from '@angular/core/testing';

import { PraatService } from './praat.service';

describe('PraatService', () => {
  let service: PraatService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PraatService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
