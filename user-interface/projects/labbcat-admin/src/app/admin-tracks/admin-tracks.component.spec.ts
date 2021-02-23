import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTracksComponent } from './admin-tracks.component';

describe('AdminTracksComponent', () => {
  let component: AdminTracksComponent;
  let fixture: ComponentFixture<AdminTracksComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminTracksComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminTracksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
