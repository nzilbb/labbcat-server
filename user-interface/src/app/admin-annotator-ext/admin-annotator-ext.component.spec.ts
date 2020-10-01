import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnnotatorExtComponent } from './admin-annotator-ext.component';

describe('AdminAnnotatorExtComponent', () => {
  let component: AdminAnnotatorExtComponent;
  let fixture: ComponentFixture<AdminAnnotatorExtComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminAnnotatorExtComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminAnnotatorExtComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
