import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EpisodeDocumentsComponent } from './episode-documents.component';

describe('EpisodeDocumentsComponent', () => {
  let component: EpisodeDocumentsComponent;
  let fixture: ComponentFixture<EpisodeDocumentsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EpisodeDocumentsComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(EpisodeDocumentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
