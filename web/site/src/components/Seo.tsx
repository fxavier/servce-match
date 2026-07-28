import type { ReactNode } from 'react';
import { Helmet } from 'react-helmet-async';

export interface SeoProps {
  title: string;
  description: string;
  canonicalPath?: string;
  jsonLd?: Record<string, unknown> | Record<string, unknown>[];
  children?: ReactNode;
}

const SITE_NAME = 'ServiMatch';
const SITE_ORIGIN = 'https://www.servimatch.pt';

/** `<title>` + `<meta description>` + Open Graph/Twitter + JSON-LD por rota (§10 — SEO). */
export function Seo({ title, description, canonicalPath, jsonLd, children }: SeoProps) {
  const fullTitle = title === SITE_NAME ? title : `${title} · ${SITE_NAME}`;
  const canonical = canonicalPath ? `${SITE_ORIGIN}${canonicalPath}` : undefined;
  const jsonLdList = jsonLd ? (Array.isArray(jsonLd) ? jsonLd : [jsonLd]) : [];

  return (
    <Helmet>
      <title>{fullTitle}</title>
      <meta name="description" content={description} />
      {canonical ? <link rel="canonical" href={canonical} /> : null}
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={description} />
      <meta property="og:type" content="website" />
      {canonical ? <meta property="og:url" content={canonical} /> : null}
      <meta name="twitter:card" content="summary_large_image" />
      <meta name="twitter:title" content={fullTitle} />
      <meta name="twitter:description" content={description} />
      {jsonLdList.map((entry, index) => (
        <script key={index} type="application/ld+json">
          {JSON.stringify(entry)}
        </script>
      ))}
      {children}
    </Helmet>
  );
}
