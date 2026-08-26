import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

export function NotFoundPage() {
  return (
    <div className="page not-found-page">
      <span>404</span>
      <h1>Station not found</h1>
      <p>The requested console route does not exist.</p>
      <Link className="primary-button" to="/"><ArrowLeft aria-hidden="true" /> Return to overview</Link>
    </div>
  );
}
