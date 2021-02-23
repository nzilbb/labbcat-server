import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminChangePasswordComponent } from './admin-change-password.component';

describe('AdminChangePasswordComponent', () => {
  let component: AdminChangePasswordComponent;
  let fixture: ComponentFixture<AdminChangePasswordComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminChangePasswordComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminChangePasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
