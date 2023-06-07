import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnnotatorTasksComponent } from './admin-annotator-tasks.component';

describe('AdminAnnotatorTasksComponent', () => {
  let component: AdminAnnotatorTasksComponent;
  let fixture: ComponentFixture<AdminAnnotatorTasksComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminAnnotatorTasksComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminAnnotatorTasksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
