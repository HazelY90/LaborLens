import { notFound } from "next/navigation";
import { pages } from "@/utils/pages";
import { StatisticsPage } from "@/features/statistics/StatisticsPage";
import { PoliciesPage } from "@/features/policies/PoliciesPage";

export default async function DataPage({ params }: { params: Promise<{ dataset: string }> }) {
  const { dataset } = await params;
  const page = pages.find((item) => item.id === dataset);
  if (!page) notFound();
  return <main id="main-content" className="page-content">
    <p className="eyebrow">{page.id === "policies" ? "Policy insights" : "Ireland · Labour market data"}</p>
    <h1>{page.title}</h1><p className="page-description">{page.description}</p>
    {page.id === "policies" ? <PoliciesPage /> : <StatisticsPage key={page.id} id={page.id} />}
  </main>;
}
