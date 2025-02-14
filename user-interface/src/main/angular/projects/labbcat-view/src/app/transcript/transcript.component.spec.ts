import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranscriptComponent } from './transcript.component';

describe('TranscriptComponent', () => {
  let component: TranscriptComponent;
  let fixture: ComponentFixture<TranscriptComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TranscriptComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TranscriptComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
