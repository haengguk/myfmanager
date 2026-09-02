import type { SVGProps } from 'react';

export type IconName =
  | 'home'
  | 'inbox'
  | 'users'
  | 'tactics'
  | 'training'
  | 'calendar'
  | 'match'
  | 'scout'
  | 'transfer'
  | 'staff'
  | 'club'
  | 'finance'
  | 'academy'
  | 'league'
  | 'search'
  | 'bell'
  | 'settings'
  | 'chevron'
  | 'filter'
  | 'file';

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
}

export function Icon({ name, ...props }: IconProps) {
  const content = {
    home: <path d="M3.5 10.5 12 3l8.5 7.5v9H15v-6H9v6H3.5z" />,
    inbox: <path d="M4 5h16v14H4zM4 13h4l2 3h4l2-3h4" />,
    users: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.2" /><path d="M3.5 20c.4-4 2.2-6 5.5-6s5.1 2 5.5 6M14 15c3.8-.7 5.8 1 6.5 5" /></>,
    tactics: <><circle cx="6" cy="6" r="2" /><circle cx="18" cy="6" r="2" /><circle cx="12" cy="18" r="2" /><path d="M8 6h8M7.2 7.6l3.6 8M16.8 7.6l-3.6 8" /></>,
    training: <path d="M5 8h3m8 0h3M8 5v6m8-6v6M8 8h8M5 14h14v6H5z" />,
    calendar: <><rect x="4" y="5" width="16" height="15" rx="1" /><path d="M8 3v4m8-4v4M4 9h16M8 13h3m2 0h3m-8 3h3" /></>,
    match: <><path d="M8 4h8v4c0 4-1.4 6-4 6S8 12 8 8zM8 6H4v2c0 2.4 1.2 3.5 4 3.5M16 6h4v2c0 2.4-1.2 3.5-4 3.5M12 14v4m-4 2h8" /></>,
    scout: <><circle cx="10.5" cy="10.5" r="6" /><path d="m15 15 5 5M8 10.5h5M10.5 8v5" /></>,
    transfer: <path d="M4 8h13m-3-3 3 3-3 3M20 16H7m3-3-3 3 3 3" />,
    staff: <><circle cx="12" cy="8" r="3" /><path d="M6 20c.5-4.2 2.5-6.2 6-6.2s5.5 2 6 6.2M4 6h3M17 6h3" /></>,
    club: <path d="M5 20V7l7-3 7 3v13M9 20v-5h6v5M8 9h2m4 0h2m-8 3h2m4 0h2" />,
    finance: <><rect x="4" y="5" width="16" height="14" rx="2" /><path d="M4 9h16m-4 4h2" /></>,
    academy: <path d="m3 9 9-5 9 5-9 5zm4 3v5c3 2.5 7 2.5 10 0v-5" />,
    league: <><path d="M7 4h10v5c0 4-1.7 6.2-5 6.2S7 13 7 9zM7 6H3v2c0 2.8 1.3 4 4.4 4M17 6h4v2c0 2.8-1.3 4-4.4 4M12 15.2V19m-4 2h8" /><path d="M10 8h4" /></>,
    search: <><circle cx="10.5" cy="10.5" r="6" /><path d="m15 15 5 5" /></>,
    bell: <path d="M18 9a6 6 0 0 0-12 0c0 6-3 7-3 7h18s-3-1-3-7M10 20h4" />,
    settings: <><circle cx="12" cy="12" r="3" /><path d="M12 3v3m0 12v3M3 12h3m12 0h3M5.6 5.6l2.1 2.1m8.6 8.6 2.1 2.1m0-12.8-2.1 2.1m-8.6 8.6-2.1 2.1" /></>,
    chevron: <path d="m14 6-6 6 6 6" />,
    filter: <path d="M4 6h16M7 12h10m-7 6h4" />,
    file: <path d="M6 3h8l4 4v14H6zM14 3v5h4M9 12h6m-6 4h6" />,
  } satisfies Record<IconName, React.ReactNode>;

  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      {content[name]}
    </svg>
  );
}
