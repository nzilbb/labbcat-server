import { TestBed } from '@angular/core/testing';

import { AdminCorporaService } from './admin-corpora.service';

describe('AdminCorporaService', () => {
  let service: AdminCorporaService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AdminCorporaService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
