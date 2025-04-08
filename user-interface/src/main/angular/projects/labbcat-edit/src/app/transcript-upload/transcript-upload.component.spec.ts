import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranscriptUploadComponent } from './transcript-upload.component';

describe('TranscriptUploadComponent', () => {
  let component: TranscriptUploadComponent;
  let fixture: ComponentFixture<TranscriptUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TranscriptUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TranscriptUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
