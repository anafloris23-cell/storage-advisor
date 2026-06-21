package ro.uaic.storageadvisor.web.dto;

/**
 * Corpul cererii POST /api/analyze-address.
 *
 * @param url o adresă de contract (0x...) sau un link Etherscan
 *            (ex: https://etherscan.io/address/0x...). Adresa este extrasă din șir.
 */
public record AnalyzeAddressRequest(String url) {}
