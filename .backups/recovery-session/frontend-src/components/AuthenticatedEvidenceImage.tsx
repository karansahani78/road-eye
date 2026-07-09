import React, { useEffect, useState } from 'react';
import api from '../services/api';
import { ViolationEvidence } from '../types';

interface Props {
  evidence: ViolationEvidence;
}

const AuthenticatedEvidenceImage: React.FC<Props> = ({ evidence }) => {
  const [src, setSrc] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    const load = async () => {
      try {
        const response = await api.get(`/evidence/${evidence.id}/file`, { responseType: 'blob' });
        if (cancelled) return;
        objectUrl = URL.createObjectURL(response.data);
        setSrc(objectUrl);
      } catch {
        if (!cancelled) setError(true);
      }
    };

    load();

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [evidence.id]);

  if (error) {
    return <div className="evidence-icon">{evidence.evidenceType}</div>;
  }

  if (!src) {
    return <div className="evidence-icon">Loading...</div>;
  }

  return <img src={src} alt={evidence.evidenceType} className="evidence-thumb" />;
};

export default AuthenticatedEvidenceImage;
