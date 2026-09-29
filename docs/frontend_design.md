# Frontend Design

Confirm page design and data requirements before designing backend APIs and implementing features.

## Access and navigation

- Visitors can access only the homepage, which introduces the project and provides a login entry. Statistical and policy data require authentication.
- After login, show five top navigation tabs. Open Annual Employment Rate by default.
- Data APIs must enforce authentication alongside page access controls.

| Page | Views | Dimensions |
| --- | --- | --- |
| Annual Employment Rate | Trend and annual comparison | Age, sex, education, region |
| Monthly Unemployment Rate | Trend | Age, sex |
| Quarterly Employment Rate | Trend and quarterly comparison | Age, sex, education |
| Quarterly Employment Count | Trend and quarterly comparison | Citizenship, economic sector |
| Policies | Category tabs and timeline | Policy type, strategy period |

## Trend view

- Display the full imported time range, with annual, monthly or quarterly ticks as appropriate.
- One filter card produces exactly one line. Each dimension in a card permits one value only.
- Start with one card using overall values. Users add separate cards to compare other combinations, and can edit or remove cards.
- Match each card's color to its line and legend. Label each line with its selected dimensions.
- Show circular markers at every available observation, including zero. Tooltips show the period, selected dimensions and value.
- Keep missing periods on the time axis, omit their markers and connect the surrounding available points directly. For example, missing 2024 data connects 2023 to 2025.
- Show a no-data state for a card with no observations. Do not sum or average selected series.

Example annual comparison:

| Card | Age | Sex | Education | Region |
| --- | --- | --- | --- | --- |
| Overall | ALL | ALL | ALL | IRELAND |
| Comparison 1 | AGE_30_34 | FEMALE | ALL | IRELAND |
| Comparison 2 | AGE_35_39 | FEMALE | ALL | IRELAND |

These three cards produce three independent lines.

## Bar comparison view

- Annual data: select one year. Quarterly data: select year and quarter separately.
- Display one comparison chart per dimension for the selected period: four for annual employment rate, three for quarterly employment rate and two for quarterly employment count.
- In each chart, vary only the compared dimension. Fix all other dimensions to their overall values; users cannot change those filters.
- Preserve each category's position when data is missing. Display an empty bar placeholder labelled `Data unavailable`, distinct from a true zero.
- Use separate bars, not stacked bars: some categories overlap or contain other categories.

Overall values use the source definitions: `IRELAND` for annual region, `AGE_15_74` for monthly age, and `ALL` for other supported dimensions. Display readable labels rather than enum codes. Employment and unemployment rates use `%`; employment counts use `thousand persons`.

## Policy view

- Show horizontal category tabs: Economic Migration, Employment Development, Skills Development, Working Conditions and Employment Inclusion.
- Place a timeline on the left and all policies in the selected category on the right.
- Group policies by source strategy period; order by start year, then end year, ascending.
- Display each policy's text, strategy period, source file and cited page. Retain distinct sources where strategy periods overlap.
- Label dates as strategy periods, not policy publication or implementation dates. Show an empty state when a category has no policies.

## Data requirements

| View | Required data |
| --- | --- |
| Shared statistics controls | Supported dimensions, allowed values, readable labels, available periods, overall defaults and unit |
| Trend | One time series per filter card, including its selected dimensions and period/value observations |
| Bar comparison | Category/value observations for each dimension at the selected period, with other dimensions fixed to overall values |
| Policies | Category, strategy start/end years, policy text, source identity and cited page |

Represent missing statistical values as `null`, never zero. Preserve complete period/category slots for chart rendering. Policy page citations currently reside in extraction artifacts; their delivery must be addressed during API design.

API routes and response contracts are the next design step; this document does not define their implementation.
