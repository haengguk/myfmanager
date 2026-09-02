import React from 'react';
import ReactDOM from 'react-dom/client';
import './App.css';
import './styles/tokens.css';
import './styles/shell.css';
import './styles/inbox.css';
import './styles/real-match.css';
import './styles/league.css';
import './styles/team-player.css';
import './styles/career.css';
import RootApp from './RootApp';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <RootApp />
  </React.StrictMode>
);
