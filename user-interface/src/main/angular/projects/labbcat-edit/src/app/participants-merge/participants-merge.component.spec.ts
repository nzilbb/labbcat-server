import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ParticipantsMergeComponent } from './participants-merge.component';

describe('ParticipantsMergeComponent', () => {
  let component: ParticipantsMergeComponent;
  let fixture: ComponentFixture<ParticipantsMergeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantsMergeComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ParticipantsMergeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
