import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminAnnotatorComponent } from './admin-annotator.component';

describe('AdminAnnotatorComponent', () => {
  let component: AdminAnnotatorComponent;
  let fixture: ComponentFixture<AdminAnnotatorComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminAnnotatorComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminAnnotatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
