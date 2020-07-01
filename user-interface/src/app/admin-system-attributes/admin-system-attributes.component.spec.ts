import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminSystemAttributesComponent } from './admin-system-attributes.component';

describe('AdminSystemAttributesComponent', () => {
  let component: AdminSystemAttributesComponent;
  let fixture: ComponentFixture<AdminSystemAttributesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminSystemAttributesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminSystemAttributesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
