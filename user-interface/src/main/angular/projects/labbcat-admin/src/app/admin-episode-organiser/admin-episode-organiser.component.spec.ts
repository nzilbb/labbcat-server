import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminEpisodeOrganiserComponent } from './admin-episode-organiser.component';

describe('AdminEpisodeOrganiserComponent', () => {
  let component: AdminEpisodeOrganiserComponent;
  let fixture: ComponentFixture<AdminEpisodeOrganiserComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminEpisodeOrganiserComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(AdminEpisodeOrganiserComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
