package com.thalys.catalogosnes.data.sync

/**
 * Itens do catálogo mestre que ainda não têm status SUCESSO gravado — usado tanto pra
 * retomar um sync interrompido quanto pra re-tentar só os que falharam (falhas não têm
 * SUCESSO gravado, então sempre voltam a aparecer aqui).
 */
fun calcularRestante(
    catalogoMestre: List<CatalogoMestreItemDto>,
    crcsComSucesso: Set<String>,
): List<CatalogoMestreItemDto> =
    catalogoMestre.filter { it.crc !in crcsComSucesso }
