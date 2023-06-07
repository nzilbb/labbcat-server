import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTranscriptTypesComponent } from './admin-transcript-types.component';

describe('AdminTranscriptTypesComponent', () => {
  let component: AdminTranscriptTypesComponent;
  let fixture: ComponentFixture<AdminTranscriptTypesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminTranscriptTypesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminTranscriptTypesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
