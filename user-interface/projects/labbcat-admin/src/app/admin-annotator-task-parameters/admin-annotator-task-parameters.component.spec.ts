import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnnotatorTaskParametersComponent } from './admin-annotator-task-parameters.component';

describe('AdminAnnotatorTaskParametersComponent', () => {
  let component: AdminAnnotatorTaskParametersComponent;
  let fixture: ComponentFixture<AdminAnnotatorTaskParametersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminAnnotatorTaskParametersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminAnnotatorTaskParametersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
