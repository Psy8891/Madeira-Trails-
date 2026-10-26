package pt.vereda.core.model

/**
 * The 42 trails classified by IFCN (39 on Madeira, 3 on Porto Santo), in official order.
 *
 * Source: Visit Madeira, "Percursos Pedestres Classificados" list (see docs/SPEC.md, section
 * "Official trail list"). This is the seed for the catalog; the data pipeline re-checks it against
 * the official list and adds geometry, stats, fees and status.
 */
object OfficialTrails {

    val all: List<Trail> = listOf(
        madeira("PR 1", "Vereda do Pico do Areeiro", "Pico do Areeiro", "Pico Ruivo", "Santana", "Pico do Arieiro"),
        madeira("PR 1.1", "Vereda da Ilha", "Pico Ruivo", "Ilha", "Santana"),
        madeira("PR 1.2", "Vereda do Pico Ruivo", "Achada do Teixeira", "Pico Ruivo", "Santana"),
        madeira("PR 1.3", "Vereda da Encumeada", "Pico Ruivo", "Encumeada", "Ribeira Brava"),
        madeira("PR 2", "Vereda do Urzal", "Curral das Freiras", "Boaventura", "Câmara de Lobos"),
        madeira("PR 3", "Vereda do Burro", "Pico do Areeiro", "Ribeira das Cales", "Funchal"),
        madeira("PR 3.1", "Caminho Real do Monte", "Ribeira das Cales", "Monte", "Funchal"),
        madeira("PR 4", "Levada do Barreiro", "Poço da Neve", "Casa do Barreiro", "Funchal"),
        madeira("PR 5", "Vereda das Funduras", "Portela", "Maroços", "Machico"),
        madeira("PR 6", "Levada das 25 Fontes", "Rabaçal", "25 Fontes", "Calheta", "25 springs"),
        madeira("PR 6.1", "Levada do Risco", "Rabaçal", "Risco", "Calheta"),
        madeira("PR 6.2", "Levada do Alecrim", "Rabaçal", "Nascente da Levada do Alecrim", "Calheta"),
        madeira("PR 6.3", "Vereda da Lagoa do Vento", "Rabaçal", "Lagoa do Vento", "Calheta"),
        madeira("PR 6.4", "Levada Velha do Rabaçal", "Pico Gordo", "Casa do Rabaçal", "Calheta"),
        madeira("PR 6.5", "Vereda do Pico Fernandes", "Paul da Serra", "25 Fontes", "Calheta"),
        madeira("PR 6.6", "Vereda do Túnel do Cavalo", "Zona de Lazer da Caldeira", "Casa do Rabaçal", "Calheta"),
        madeira("PR 6.7", "Vereda da Câmara de Carga do Rabaçal", "Rabaçal", "Túnel do Cavalo", "Calheta"),
        madeira("PR 6.8", "Levada do Paul II – Um Caminho Para Todos", "Rabaçal", "Estrada do Rochão", "Calheta"),
        madeira("PR 7", "Levada do Moinho", "Ribeira da Cruz", "Lamaceiros", "Porto Moniz"),
        madeira("PR 8", "Vereda da Ponta de São Lourenço", "Baía d'Abra", "Cais do Sardinha", "Machico"),
        madeira("PR 9", "Levada do Caldeirão Verde", "Queimadas", "Caldeirão Verde", "Santana"),
        madeira("PR 9.1", "Levada do Caldeirão Verde – Um Caminho para Todos", "Queimadas", "Pico das Pedras", "Santana"),
        madeira("PR 10", "Levada do Furado", "Ribeiro Frio", "Portela", "Machico"),
        madeira("PR 11", "Vereda dos Balcões", "Ribeiro Frio", "Balcões", "Santana"),
        madeira("PR 12", "Caminho Real da Encumeada", "Boca da Corrida", "Ribeira Grande", "Ribeira Brava"),
        madeira("PR 13", "Vereda do Fanal", "Assobiadores", "Fanal", "Porto Moniz"),
        madeira("PR 13.1", "Vereda da Palha Carga", "Louro da Gota", "25 Fontes", "Porto Moniz"),
        madeira("PR 14", "Levada dos Cedros", "Fanal", "Curral Falso", "Porto Moniz"),
        madeira("PR 15", "Vereda da Ribeira da Janela", "Curral Falso", "Ribeira da Janela", "Porto Moniz"),
        madeira("PR 16", "Levada Fajã do Rodrigues", "Fajã da Amã", "Ribeira da Janela", "São Vicente"),
        madeira("PR 17", "Caminho do Pináculo e Folhadal", "Lombo do Mouro", "Encumeada", "Ribeira Brava"),
        madeira("PR 18", "Levada do Rei", "Quebradas", "Ribeiro Bonito", "Santana"),
        madeira("PR 19", "Caminho Real do Paul do Mar", "Prazeres", "Paul do Mar", "Calheta"),
        madeira("PR 20", "Vereda do Jardim do Mar", "Prazeres", "Jardim do Mar", "Calheta"),
        madeira("PR 21", "Caminho do Norte", "Encumeada", "Ribeira Grande", "São Vicente"),
        madeira("PR 22", "Vereda do Chão dos Louros", null, null, "São Vicente"),
        madeira("PR 23", "Levada da Azenha", "Azenha", "Caminho Velho do Castelo", "Santa Cruz"),
        madeira("PR 27", "Glaciar do Planalto", "Bica da Cana", "ER105", "Calheta"),
        madeira("PR 28", "Levada da Rocha Vermelha", "Fanal", "Lombo do Atouguia", "Calheta"),
        portoSanto("PS PR 1", "Vereda do Pico Branco e Terra Chã", "ER 111", "Terra Chã"),
        portoSanto("PS PR 2", "Vereda do Pico do Castelo", "Moledo", "Pico do Castelo"),
        portoSanto("PS PR 3", "Levada do Pico do Castelo", "Camacha", "Capela da Graça"),
    )

    fun byId(id: String): Trail? = all.firstOrNull { it.id == id }

    private fun madeira(
        code: String,
        name: String,
        from: String?,
        to: String?,
        municipality: String,
        vararg aliases: String,
    ) = Trail(
        id = Trail.idForCode(code),
        source = TrailSource.OFFICIAL,
        code = code,
        name = name,
        from = from,
        to = to,
        municipality = municipality,
        island = Island.MADEIRA,
        aliases = aliases.toList(),
    )

    private fun portoSanto(code: String, name: String, from: String, to: String) = Trail(
        id = Trail.idForCode(code),
        source = TrailSource.OFFICIAL,
        code = code,
        name = name,
        from = from,
        to = to,
        municipality = "Porto Santo",
        island = Island.PORTO_SANTO,
    )
}
