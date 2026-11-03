import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ParticipantsLayersRegenerateComponent } from './participants-layers-regenerate.component';

describe('ParticipantsLayersRegenerateComponent', () => {
  let component: ParticipantsLayersRegenerateComponent;
  let fixture: ComponentFixture<ParticipantsLayersRegenerateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantsLayersRegenerateComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ParticipantsLayersRegenerateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
