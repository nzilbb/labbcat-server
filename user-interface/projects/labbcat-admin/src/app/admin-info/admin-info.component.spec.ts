import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminInfoComponent } from './admin-info.component';

describe('AdminInfoComponent', () => {
  let component: AdminInfoComponent;
  let fixture: ComponentFixture<AdminInfoComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminInfoComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
