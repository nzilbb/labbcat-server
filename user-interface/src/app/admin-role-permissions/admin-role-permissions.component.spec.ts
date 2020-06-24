import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminRolePermissionsComponent } from './admin-role-permissions.component';

describe('AdminRolePermissionsComponent', () => {
  let component: AdminRolePermissionsComponent;
  let fixture: ComponentFixture<AdminRolePermissionsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminRolePermissionsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminRolePermissionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
