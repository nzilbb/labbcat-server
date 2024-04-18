import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranscriptMediaComponent } from './transcript-media.component';

describe('TranscriptMediaComponent', () => {
  let component: TranscriptMediaComponent;
  let fixture: ComponentFixture<TranscriptMediaComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TranscriptMediaComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TranscriptMediaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
