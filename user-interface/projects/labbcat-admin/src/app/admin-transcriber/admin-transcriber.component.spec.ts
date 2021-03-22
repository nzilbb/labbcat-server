import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminTranscriberComponent } from './admin-transcriber.component';

describe('AdminTranscriberComponent', () => {
  let component: AdminTranscriberComponent;
  let fixture: ComponentFixture<AdminTranscriberComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AdminTranscriberComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AdminTranscriberComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
