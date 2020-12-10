import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DiscHelperComponent } from './disc-helper.component';

describe('DiscHelperComponent', () => {
  let component: DiscHelperComponent;
  let fixture: ComponentFixture<DiscHelperComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ DiscHelperComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DiscHelperComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
