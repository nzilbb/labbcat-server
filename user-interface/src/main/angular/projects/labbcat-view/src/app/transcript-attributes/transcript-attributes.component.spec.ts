import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TranscriptAttributesComponent } from './transcript-attributes.component';

describe('TranscriptAttributesComponent', () => {
  let component: TranscriptAttributesComponent;
  let fixture: ComponentFixture<TranscriptAttributesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ TranscriptAttributesComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(TranscriptAttributesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
