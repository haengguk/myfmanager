export type InboxTab = 'all' | 'social' | 'team' | 'league' | 'transfer';

export type MessageTone = 'teal' | 'violet' | 'amber' | 'red' | 'green';

export interface InboxParagraph {
  readonly text: string;
  readonly emphasis?: string;
  readonly suffix?: string;
}

export interface InboxMetric {
  readonly label: string;
  readonly value: string;
}

export interface InboxContextRow {
  readonly label: string;
  readonly current: string;
  readonly recommendation: string;
  readonly note: string;
}

export interface InboxAttachment {
  readonly name: string;
  readonly meta: string;
}

export interface InboxMessage {
  readonly id: string;
  readonly tab: Exclude<InboxTab, 'all'>;
  readonly date: string;
  readonly sender: string;
  readonly initials: string;
  readonly role: string;
  readonly department: string;
  readonly subject: string;
  readonly time: string;
  readonly category: string;
  readonly tone: MessageTone;
  readonly important: boolean;
  readonly initiallyUnread: boolean;
  readonly preview: string;
  readonly summary: string;
  readonly body: readonly InboxParagraph[];
  readonly metrics: readonly InboxMetric[];
  readonly contextTitle: string;
  readonly rows: readonly InboxContextRow[];
  readonly attachment: InboxAttachment;
  readonly actions: readonly string[];
}

export interface ToastMessage {
  readonly title: string;
  readonly message: string;
}
