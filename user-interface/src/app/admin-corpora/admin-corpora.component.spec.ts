import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminCorporaComponent } from './admin-corpora.component';

describe('AdminCorporaComponent', () => {
  let component: AdminCorporaComponent;
  let fixture: ComponentFixture<AdminCorporaComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminCorporaComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminCorporaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
