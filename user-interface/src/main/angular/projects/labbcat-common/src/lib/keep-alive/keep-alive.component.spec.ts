import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { KeepAliveComponent } from './keep-alive.component';

describe('KeepAliveComponent', () => {
  let component: KeepAliveComponent;
  let fixture: ComponentFixture<KeepAliveComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ KeepAliveComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(KeepAliveComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
