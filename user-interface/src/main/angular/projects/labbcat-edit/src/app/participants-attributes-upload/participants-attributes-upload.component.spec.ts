import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ParticipantsAttributesUploadComponent } from './participants-upload.component';

describe('ParticipantsAttributesUploadComponent', () => {
  let component: ParticipantsAttributesUploadComponent;
  let fixture: ComponentFixture<ParticipantsAttributesUploadComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantsAttributesUploadComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ParticipantsAttributesUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
