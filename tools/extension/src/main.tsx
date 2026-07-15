import { render } from "preact";
import { AppStateProvider } from "./app/app-state";
import { App } from "./App";
import "./styles.css";

render(<AppStateProvider><App /></AppStateProvider>, document.getElementById("app")!);
