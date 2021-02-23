import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminRoleUsersComponent } from './admin-role-users.component';

describe('AdminRoleUsersComponent', () => {
  let component: AdminRoleUsersComponent;
  let fixture: ComponentFixture<AdminRoleUsersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminRoleUsersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminRoleUsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
