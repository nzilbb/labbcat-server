import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupedCheckboxComponent } from './grouped-checkbox.component';

describe('GroupedCheckboxComponent', () => {
  let component: GroupedCheckboxComponent;
  let fixture: ComponentFixture<GroupedCheckboxComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ GroupedCheckboxComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(GroupedCheckboxComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
