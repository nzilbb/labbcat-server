import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminLayersComponent } from './admin-layers.component';

describe('AdminLayersComponent', () => {
  let component: AdminLayersComponent;
  let fixture: ComponentFixture<AdminLayersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminLayersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminLayersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
