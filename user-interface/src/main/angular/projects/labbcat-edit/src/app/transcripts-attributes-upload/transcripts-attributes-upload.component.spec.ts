import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranscriptAttributesUploadComponent } from './transcript-attributes-upload.component';

describe('TranscriptAttributesUploadComponent', () => {
  let component: TranscriptAttributesUploadComponent;
  let fixture: ComponentFixture<TranscriptAttributesUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TranscriptAttributesUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TranscriptAttributesUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
