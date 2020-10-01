import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnnotatorsComponent } from './admin-annotators.component';

describe('AdminAnnotatorsComponent', () => {
  let component: AdminAnnotatorsComponent;
  let fixture: ComponentFixture<AdminAnnotatorsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminAnnotatorsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminAnnotatorsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
