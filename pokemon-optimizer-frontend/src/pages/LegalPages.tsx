import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { PageHeader } from '../components/PageHeader'
import { Feedback } from '../components/Feedback'
import { usePageTitle } from '../lib/page-title'
import { ANALYTICS_ENABLED } from '../lib/analytics'

const UPDATED = 'November 10, 2026'

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-10">
      <h2 className="font-display text-xl text-ink">{title}</h2>
      <div className="mt-3 space-y-3 text-ink-soft [&_li]:ml-5 [&_li]:list-disc [&_strong]:text-ink">{children}</div>
    </section>
  )
}

function LegalPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mx-auto max-w-3xl px-4 pb-20">
      <PageHeader title={title}>
        <p className="text-sm text-muted">Last updated {UPDATED}</p>
      </PageHeader>
      <div className="card p-6 leading-relaxed sm:p-8">{children}</div>
    </div>
  )
}

export function PrivacyPage() {
  usePageTitle('Privacy Policy')
  return (
    <LegalPage title="Privacy Policy">
      <p className="text-ink-soft">
        DexPresso is a free fan-made tool for Pokémon GO PvP. It keeps as little about you as it can. This page lists
        all of it.
      </p>

      <Section title="Without an Account">
        <p>
          You can use everything without logging in. Pokémon you add as a guest are saved <strong>only in your
          browser</strong> (localStorage), never on our server. Clearing your browser data deletes them.
        </p>
        <p>
          Files you upload to import or build Teams (Poké Genie or Calcy IV exports) are read to answer that one request
          and are not stored.
        </p>
      </Section>

      <Section title="With an Account (Google Login)">
        <p>When you log in with Google, we store:</p>
        <ul>
          <li>your Google account ID, a number that links your login to your Collection, and</li>
          <li>the Pokémon you save: species, IVs, level, moves and whether it's shadow.</li>
        </ul>
        <p>
          Google also tells us your name when you log in, to show it in the menu. It's kept with your login session,
          which is stored in our database so a site update doesn't log you out, and{' '}
          <strong>deleted when you sign out or after 30 days without a visit</strong>. We don't ask Google for your
          email address.
        </p>
      </Section>

      <Section title="Feedback and Ratings">
        <p>
          Feedback messages and star ratings are anonymous. We keep what you write (or the stars you pick), the page you
          sent it from and when. They aren't linked to your account, your IP address or your browser.
        </p>
      </Section>

      <Section title="Team Builder Runs">
        <p>
          Each time the Team Builder finishes, we count it: the league or Cup, how many Pokémon went in, and when. Not which
          Pokémon, and nothing that links the run to you, your account, your IP address or your browser. Totals may be shown
          on the site.
        </p>
      </Section>

      <Section title="Cookies and Browser Storage">
        <p>DexPresso uses two cookies, both needed for the site to work, so there's no cookie banner:</p>
        <ul>
          <li>
            <strong>JSESSIONID</strong> keeps you logged in until you sign out, or for 30 days after your last visit.
          </li>
          <li>
            <strong>XSRF-TOKEN</strong> stops other sites from making changes to your account on your behalf.
          </li>
        </ul>
        <p>
          In localStorage your browser also keeps your guest Collection and whether you've already left a star rating.
        </p>
      </Section>

      <Section title="Other Services Your Browser Talks To">
        <ul>
          <li>
            <strong>Google</strong>, only when you choose to log in. Google's own privacy policy applies there.
          </li>
          <li>
            <strong>GitHub</strong> (raw.githubusercontent.com), which hosts the Pokémon artwork from PokéAPI. Like any
            website, it sees your IP address when your browser loads an image.
          </li>
          {ANALYTICS_ENABLED && (
            <li>
              <strong>GoatCounter</strong>, a privacy-friendly visitor counter. It counts page views without cookies and
              without storing IP addresses or tracking you across sites.
            </li>
          )}
        </ul>
        <p>There are no ads, no tracking pixels, and we never sell or share your data.</p>
      </Section>

      <Section title="Server Logs and Abuse Protection">
        <p>
          To keep the site up, the server briefly remembers how many requests each IP address made in the last few
          minutes. That count lives in memory only and is never saved. Error logs record which page failed, not who
          was using it. Our hosting provider may keep standard access logs for a short time.
        </p>
      </Section>

      <Section title="Deleting Your Data">
        <p>
          Open the account menu (top right, once logged in) and choose <strong>Delete account</strong>. It permanently
          deletes your account ID and every Pokémon in your Collection, right away. Anonymous feedback and ratings
          can't be traced back to you, so they stay.
        </p>
      </Section>

      <Section title="Children">
        <p>
          Accounts are for people <strong>13 and older</strong>. If you're younger, use DexPresso without logging in:
          guest mode keeps everything in your own browser and sends nothing about you to us. If you believe a child
          under 13 has made an account, tell us using the feedback form and we'll delete it.
        </p>
      </Section>

      <Section title="Contact and Changes">
        <p>
          Questions or requests: <Feedback variant="link" />. If this policy changes, the date at the top changes too.
        </p>
      </Section>
    </LegalPage>
  )
}

export function TermsPage() {
  usePageTitle('Terms of Use')
  return (
    <LegalPage title="Terms of Use">
      <p className="text-ink-soft">By using DexPresso you agree to these terms. They're short.</p>

      <Section title="What DexPresso Is">
        <p>
          A free, unofficial fan tool that ranks Pokémon GO IVs for PvP leagues and suggests Teams from your Pokémon.
          There are no paid plans and nothing to buy.
        </p>
      </Section>

      <Section title="Not Affiliated">
        <p>
          DexPresso is not affiliated with, endorsed by or sponsored by Niantic, Nintendo, Creatures, GAME FREAK or The
          Pokémon Company. Pokémon and Pokémon character names are trademarks of Nintendo, Creatures and GAME FREAK.
          Pokémon artwork is loaded from PokéAPI and belongs to its owners. Game data comes from PvPoke's open-source
          gamemaster.
        </p>
      </Section>

      <Section title="No Guarantees">
        <p>
          Rankings, win odds and Team suggestions are estimates from a battle simulation. They can be wrong or out of
          date after a game update. DexPresso is provided as is, without warranties of any kind, and we aren't liable
          for any loss from using it, including a lost battle or a Pokémon you powered up on our advice.
        </p>
      </Section>

      <Section title="Fair Use">
        <ul>
          <li>Don't try to break, overload or scrape the site, or get around its rate limits.</li>
          <li>Don't use it to break the rules of Pokémon GO.</li>
          <li>Accounts are for people 13 and older (see the <Link to="/privacy" className="text-brand underline">privacy policy</Link>).</li>
        </ul>
        <p>We may limit or remove access for anyone who abuses the service.</p>
      </Section>

      <Section title="Your Data and Changes">
        <p>
          You can delete your account at any time from the account menu. We may change or stop the service, or update
          these terms; the date at the top shows the latest version.
        </p>
      </Section>
    </LegalPage>
  )
}
