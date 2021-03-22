import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTranscribersComponent } from './admin-transcribers.component';

describe('AdminTranscribersComponent', () => {
  let component: AdminTranscribersComponent;
  let fixture: ComponentFixture<AdminTranscribersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminTranscribersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminTranscribersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
