import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminLayerLabelsComponent } from './admin-layer-labels.component';

describe('AdminLayerLabelsComponent', () => {
  let component: AdminLayerLabelsComponent;
  let fixture: ComponentFixture<AdminLayerLabelsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AdminLayerLabelsComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminLayerLabelsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
