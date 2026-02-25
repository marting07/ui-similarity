import { Component } from '@angular/core';

@Component({
  selector: 'inline-card',
  template: `<section role="region"><h2>Inline</h2></section>`,
  styles: [`.card { margin: 6px; }`]
})
export class FancyCard {}
