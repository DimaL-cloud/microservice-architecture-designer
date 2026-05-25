import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Topbar } from './topbar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Topbar],
  templateUrl: './app.html'
})
export class App {}
