"""Legacy entry point retained for deployment compatibility.

Document events are now consumed by the Spring Boot worker component through the
provider-neutral DocumentIntelligencePort. This process deliberately does not
produce clauses, pages, or READY results.
"""

import logging


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    logging.getLogger("specai-document-worker").warning(
        "Legacy Python worker is disabled; use the backend DocumentIntelligencePort consumer"
    )


if __name__ == "__main__":
    main()
