import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranscriptsLayersRegenerateComponent } from './transcripts-layers-regenerate.component';

describe('TranscriptsLayersRegenerateComponent', () => {
  let component: TranscriptsLayersRegenerateComponent;
  let fixture: ComponentFixture<TranscriptsLayersRegenerateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TranscriptsLayersRegenerateComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(TranscriptsLayersRegenerateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
