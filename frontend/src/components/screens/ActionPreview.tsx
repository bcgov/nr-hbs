import { InlineLoading, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react';
import { useEffect, useState, type FC } from 'react';

import { runCommand, type Row } from '@/services/screens';
import type { ActionDef } from '@/screens/types';

import { formatValue } from './format';

interface PreviewVersion {
  documentControlNumber: number;
  version: number;
  returnStateCode: string;
  status: string;
  transactions: Row[];
}

/**
 * The legacy confirmation screen (P054/P104/P154) inside an action's modal: the
 * versions the action touches and the transactions it will produce, from the
 * action's read-only `preview` command.
 */
export const ActionPreview: FC<{ action: ActionDef; record: Row }> = ({ action, record }) => {
  const [versions, setVersions] = useState<PreviewVersion[] | null>(null);
  const [error, setError] = useState<string | undefined>();

  useEffect(() => {
    if (!action.preview) return;
    let cancelled = false;
    const body: Record<string, unknown> = { action: action.id };
    for (const [arg, key] of Object.entries(action.preview.params)) {
      body[arg] = key.startsWith('=') ? key.slice(1) : record[key];
    }
    runCommand(action.preview.command, body)
      .then((r) => !cancelled && setVersions((r.returns as PreviewVersion[]) ?? []))
      .catch((e) => !cancelled && setError((e as Error).message));
    return () => {
      cancelled = true;
    };
  }, [action, record]);

  if (error) return <p className="detail-dialog__subtitle">Preview unavailable: {error}</p>;
  if (!versions) return <InlineLoading description="Loading confirmation details…" />;
  return (
    <>
      {versions.map((v) => (
        <div key={`${v.documentControlNumber}-${v.version}`} className="hbs-action-preview">
          <p className="detail-dialog__subtitle">
            SDN {v.documentControlNumber}, version {v.version} — {v.returnStateCode} / {v.status}
          </p>
          <Table size="sm" aria-label={`Transactions of version ${v.version}`}>
            <TableHead>
              <TableRow>
                <TableHeader>Txn Seq</TableHeader>
                <TableHeader>DR/CR</TableHeader>
                <TableHeader>Txn Type</TableHeader>
                <TableHeader>Invoice / Statement</TableHeader>
                <TableHeader>Volume</TableHeader>
                <TableHeader>Value</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {v.transactions.map((t, i) => (
                <TableRow key={i}>
                  <TableCell>{formatValue(t.eventSequence, 'number')}</TableCell>
                  <TableCell>{String(t.drCr ?? '')}</TableCell>
                  <TableCell>{String(t.txnTypeDesc ?? '')}</TableCell>
                  <TableCell>{String(t.invoiceNumber ?? '')}</TableCell>
                  <TableCell>{formatValue(t.volume, 'volume')}</TableCell>
                  <TableCell>{formatValue(t.value, 'money')}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ))}
    </>
  );
};

export default ActionPreview;
