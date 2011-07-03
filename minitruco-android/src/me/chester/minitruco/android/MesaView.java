package me.chester.minitruco.android;

import java.util.Random;
import java.util.Vector;

import me.chester.minitruco.core.Carta;
import me.chester.minitruco.core.Jogador;
import me.chester.minitruco.core.Jogo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/*
 * Copyright © 2005-2011 Carlos Duarte do Nascimento (Chester)
 * cd@pobox.com
 * 
 * Este programa é um software livre; você pode redistribui-lo e/ou 
 * modifica-lo dentro dos termos da Licença Pública Geral GNU como 
 * publicada pela Fundação do Software Livre (FSF); na versão 3 da 
 * Licença, ou (na sua opnião) qualquer versão.
 *
 * Este programa é distribuido na esperança que possa ser util, 
 * mas SEM NENHUMA GARANTIA; sem uma garantia implicita de ADEQUAÇÂO
 * a qualquer MERCADO ou APLICAÇÃO EM PARTICULAR. Veja a Licença
 * Pública Geral GNU para maiores detalhes.
 *
 * Você deve ter recebido uma cópia da Licença Pública Geral GNU
 * junto com este programa, se não, escreva para a Fundação do Software
 * Livre(FSF) Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

/**
 * Representa visualmente o andamento de um jogo, permitindo que o usuário
 * interaja.
 * <p>
 * Para simplificar o acesso, alguns métodos/propriedades são static - o que só
 * reitera que só deve existir uma instância desta View.
 * 
 * @author chester
 * 
 */
public class MesaView extends View {

	public MesaView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public MesaView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public MesaView(Context context) {
		super(context);
	}

	private Rect rectDialog;

	private RectF rectBotaoSim;

	private RectF rectBotaoNao;

	private float tamanhoFonte;

	/**
	 * Informa à mesa que uma animação começou (garantindo refreshes da tela
	 * enquanto ela durar).
	 * 
	 * @param fim
	 *            timestamp de quando a animação vai acabar
	 */
	public static void notificaAnimacao(long fim) {
		if (animandoAte < fim) {
			animandoAte = fim;
		}
	}

	/**
	 * Coloca a thread chamante em sleep até que as animações acabem.
	 */
	public void aguardaFimAnimacoes() {
		long milisecAteFimAnimacao;
		while ((milisecAteFimAnimacao = animandoAte
				- System.currentTimeMillis()) > 0) {
			try {
				Thread.sleep(milisecAteFimAnimacao);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	/**
	 * Cartas que estão na mesa, na ordem de empilhamento. cartas[0] é o vira,
	 * cartas[1..3] são o baralho decorativo, cartas[4..6] são as do jogador na
	 * posição 1 (inferior), cartas[7..9] o jogador 2 e assim por diante para os
	 * jogadores 3 e 4.
	 */
	public CartaVisual[] cartas = new CartaVisual[16];

	/**
	 * Ajusta o tamanho das cartas e sua posição de acordo com a resolução.
	 * <p>
	 * Na primeira chamada, incializa as cartas e dá início à partida (tem que
	 * ser feito aqui porque não é uma boa idéia começar sem saber onde as
	 * coisas vão aparecer)
	 */
	@Override
	public void onSizeChanged(int w, int h, int oldw, int oldh) {

		// Ajusta o tamanho da carta (tudo depende dele) ao da mesa e os
		// "pontos de referência" importantes (baralho decorativo, tamanho do
		// texto, etc.)
		CartaVisual.ajustaTamanho(w, h);
		leftBaralho = w - CartaVisual.largura - MARGEM - 4;
		topBaralho = h - CartaVisual.altura - MARGEM - 4;
		tamanhoFonte = 12.0f * (h / 270.0f);

		// Na primeira chamada (inicialização), instanciamos as cartas
		boolean inicializando = (cartas[0] == null);
		if (inicializando) {
			for (int i = 0; i < cartas.length; i++) {
				cartas[i] = new CartaVisual(this, leftBaralho, topBaralho, null);
				cartas[i].movePara(leftBaralho, topBaralho);
			}
			cartas[0].visible = false;
		}

		// Define posição e tamanho da caixa de diálogo e seus botões
		int alturaDialog = CartaVisual.altura;
		int larguraDialog = CartaVisual.largura * 3;
		int topDialog = (h - alturaDialog) / 2;
		int leftDialog = (w - larguraDialog) / 2;
		rectDialog = new Rect(leftDialog, topDialog,
				leftDialog + larguraDialog, topDialog + alturaDialog);
		int alturaBotao = (int) (tamanhoFonte * 1.8f);
		rectBotaoSim = new RectF(leftDialog + 8, topDialog + alturaDialog
				- alturaBotao - 8, leftDialog + larguraDialog / 2 - 8,
				topDialog + alturaDialog - 8);
		rectBotaoNao = new RectF(leftDialog + larguraDialog / 2 + 8,
				rectBotaoSim.top, leftDialog + larguraDialog - 8,
				rectBotaoSim.bottom);

		// Posiciona o vira e as cartas decorativas do baralho, que são fixos
		cartas[0].movePara(leftBaralho, topBaralho);
		cartas[1].movePara(leftBaralho + 4, topBaralho + 4);
		cartas[2].movePara(leftBaralho + 2, topBaralho + 2);

		if (inicializando) {
			// Inicia as threads internas que cuidam de animações e de responder
			// a diálogos, bem como a do jogo em si.
			animacaoJogo.start();
			respondeDialogos.start();
			(new Thread(MenuPrincipalActivity.jogo)).start();
		} else {
			// Rolou um resize, reposiciona as cartas não-decorativas
			for (int i = 0; i <= 15; i++) {
				CartaVisual cv = cartas[i];
				if (cv != null) {
					cv.resetBitmap();
					if (i >= 4) {
						int numJogador = (i - 1) / 3;
						if (cv.descartada) {
							cv.movePara(getPosLeftDescartada(numJogador),
									getPosTopDescartada(numJogador));
						} else {
							int pos = (i - 1) % 3;
							cv.movePara(getPosLeftCarta(numJogador, pos),
									getPosTopCarta(numJogador, pos));
						}
					}
				}
			}
		}
	}

	/**
	 * Recupera a carta visual correspondente a uma carta do jogo.
	 * 
	 * @param c
	 *            carta do jogo
	 * @return Carta visual com o valor desta, ou <code>null</code> se não achar
	 */
	public CartaVisual getCartaVisual(Carta c) {
		for (CartaVisual cv : cartas) {
			if (c != null && c.equals(cv)) {
				return cv;
			}
		}
		return null;
	}

	/**
	 * Atualiza o resultado de uma rodada, destacando a carta vencedora e
	 * piscando a rodada atual por um instante.
	 * 
	 * @param numRodada
	 *            rodada que finalizou
	 * @param resultado
	 *            (0 a 3, vide {@link #resultadoRodada}
	 * @param jogadorQueTorna
	 *            jogador cuja carta venceu a rodada
	 */
	public void atualizaResultadoRodada(int numRodada, int resultado,
			Jogador jogadorQueTorna) {
		cartaQueFez = getCartaVisual(jogo.getCartasDaRodada(numRodada)[jogadorQueTorna
				.getPosicao() - 1]);
		cartaQueFez.destacada = true;
		for (CartaVisual c : cartas) {
			c.escura = c.descartada;
		}
		resultadoRodada[numRodada - 1] = resultado;
		numRodadaPiscando = numRodada;
		rodadaPiscaAte = System.currentTimeMillis() + 1600;
		notificaAnimacao(rodadaPiscaAte);
	}

	/**
	 * Torna as cartas da mão de 11 visíveis
	 * 
	 * @param cartasParceiro
	 *            cartas do seu parceiro
	 */
	public void mostraCartasMao11(Carta[] cartasParceiro) {
		for (int i = 0; i <= 2; i++) {
			cartas[10 + i].setCarta(cartasParceiro[i]);
		}
	}

	/**
	 * Faz com que o balão mostre uma frase por um tempo para um jogador.
	 * <p>
	 * As frases estão no strings.xml no formato balao_<chave>, e são arrays de
	 * strings (das quais uma será sorteada para exibição).
	 * 
	 * @param chave
	 *            diz o tipo de texto que aparece no balão. Ex.: "aumento_3"
	 *            para pedido de truco.
	 * @param posicao
	 *            posição (1 a 4) do jogador que "dirá" a frase
	 * @param tempoMS
	 *            tempo em que ela aparecerá
	 */
	public void diz(String chave, int posicao, int tempoMS) {
		mostraBalaoAte = System.currentTimeMillis() + tempoMS;
		Resources res = getResources();
		String[] frasesBalao = res.getStringArray(res.getIdentifier("balao_"
				+ chave, "array", "me.chester.minitruco"));
		fraseBalao = frasesBalao[(new Random()).nextInt(frasesBalao.length)];
		posicaoBalao = posicao;
		notificaAnimacao(mostraBalaoAte);
	}

	/**
	 * Verifica qual elemento foi tocado (ex.: uma das cartas do jogador ou um
	 * dos botões) e executa a ação associada a ele.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (mostrarPerguntaMao11) {
				if (rectBotaoSim.contains((int) event.getX(), (int) event
						.getY())) {
					mostrarPerguntaMao11 = false;
					aceitarMao11 = true;
				}
				if (rectBotaoNao.contains((int) event.getX(), (int) event
						.getY())) {
					mostrarPerguntaMao11 = false;
					recusarMao11 = true;
				}
			}
			if (mostrarPerguntaAumento) {
				if (rectBotaoSim.contains((int) event.getX(), (int) event
						.getY())) {
					mostrarPerguntaAumento = false;
					aceitarAumento = true;
				}
				if (rectBotaoNao.contains((int) event.getX(), (int) event
						.getY())) {
					mostrarPerguntaAumento = false;
					recusarAumento = true;
				}
			}
			if (vezHumano == 1) {
				for (int i = 6; i >= 4; i--) {
					if ((!cartas[i].descartada)
							&& cartas[i].isDentro(event.getX(), event.getY())) {
						vezHumano = 0;
						cartas[i].setFechada(vaiJogarFechada);
						jogo.jogaCarta(jogo.getJogadorHumano(), cartas[i]);
					}
				}
			}
		}
		return super.onTouchEvent(event);
	}

	private long calcTempoAteFimAnimacaoMS() {
		return animandoAte - System.currentTimeMillis();
	}

	/**
	 * Thread/runnable que faz as animações acontecerem.
	 * <p>
	 */
	Thread animacaoJogo = new Thread(new Runnable() {

		// Para economizar CPU/bateria, o jogo trabalha a um máximo de 4 FPS
		// (1000/(200+50)) quando não tem nenhuma animação rolando, e sobe para
		// um máximo de 20 FPS (1000/50) quando tem (é sempre um pouco menos
		// porque periga não ter dado tempo de redesenhar a tela entre um
		// postInvalidate() e outro.
		public void run() {
			// Aguarda o jogo começar
			while (jogo == null) {
				sleep(200);
			}
			// Fica em loop até o jogo acabar
			while (!jogo.jogoFinalizado) {
				sleep(200);
				do {
					if (visivel) {
						postInvalidate();
					}
					sleep(50);
				} while (calcTempoAteFimAnimacaoMS() >= 0);
			}
		}

		private void sleep(int tempoMS) {
			try {
				Thread.sleep(tempoMS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	});

	Thread respondeDialogos = new Thread() {
		@Override
		public void run() {
			// Aguarda o jogo começar
			while (jogo == null) {
				try {
					sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			// Roda até o jogo acabar
			while (!jogo.jogoFinalizado) {
				try {
					sleep(250);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if (recusarMao11) {
					recusarMao11 = false;
					jogo.decideMao11(jogo.getJogadorHumano(), false);
				}
				if (aceitarMao11) {
					aceitarMao11 = false;
					jogo.decideMao11(jogo.getJogadorHumano(), true);
				}
				if (recusarAumento) {
					recusarAumento = false;
					jogo.respondeAumento(jogo.getJogadorHumano(), false);
				}
				if (aceitarAumento) {
					aceitarAumento = false;
					jogo.respondeAumento(jogo.getJogadorHumano(), true);
				}
			}
		}

	};

	/**
	 * Permite à partida informar que (não) é a vez de deixar o humano jogar
	 * 
	 * @param vezHumano
	 *            true se for a vez dele, false se não
	 * @param podeFechada
	 *            true se o jogador da vez pode jogar a carta fechada
	 */
	public static void setVezHumano(boolean vezHumano, boolean podeFechada) {
		MesaView.vezHumano = vezHumano ? 1 : 0;
	}

	/**
	 * Entrega as cartas iniciais na mão de cada jogador
	 * 
	 * @cartas array com as três cartas do jogador na posição 1. Se
	 *         <code>null</code>, elas vêm fechadas como as dos outros
	 */
	public void distribuiMao() {

		// Distribui as cartas em círculo
		for (int i = 0; i <= 2; i++) {
			for (int j = 1; j <= 4; j++) {
				Carta c = null;
				JogadorHumano jh = jogo.getJogadorHumano();
				if (j == 1 && jh != null) {
					c = jh.getCartas()[i];
				}
				distribui(j, i, c);
			}
		}

		// Abre o vira, se for manilha nova
		if (!jogo.isManilhaVelha()) {
			cartas[0].setCarta(jogo.cartaDaMesa);
			cartas[0].visible = true;
		}

	}

	/**
	 * Entrega uma carta na posição apropriada
	 * <p>
	 * 
	 * @param numJogador
	 *            Posição do jogador, de 1 a 4 (1 = humano).
	 * @param i
	 *            posição da carta na mão do jogador (0 a 2)
	 * @valor Carta (do jogo, não visual) que foi jogada. Se <code>null</code>,
	 *        entrega fechada (sem valor)
	 */
	private void distribui(int numJogador, int i, Carta carta) {
		// Para o jogador da posição superior, inverte a ordem
		// (senão a exibição na mão de 11 fica bagunçada)
		if (numJogador == 3 || numJogador == 4) {
			i = 2 - i;
		}

		// Adiciona a carta na mesa, em cima do baralho, e anima até a posição
		CartaVisual c = cartas[4 + i + 3 * (numJogador - 1)];
		c.setCarta(carta);
		// c.movePara(topBaralho, leftBaralho);
		c.movePara(getPosLeftCarta(numJogador, i),
				getPosTopCarta(numJogador, i), 150);
	}

	/**
	 * 
	 * @param numJogador
	 * @param i
	 * @return Posição (x) da i-ésima carta na mão do jogador em questão
	 */
	private int getPosLeftCarta(int numJogador, int i) {
		int deslocamentoHorizontalEntreCartas = CartaVisual.largura * 7 / 8;
		int leftFinal = 0;
		switch (numJogador) {
		case 1:
		case 3:
			leftFinal = (getWidth() / 2) - (CartaVisual.largura / 2) + (i - 1)
					* deslocamentoHorizontalEntreCartas;
			break;
		case 2:
			leftFinal = getWidth() - CartaVisual.largura - MARGEM;
			break;
		case 4:
			leftFinal = MARGEM;
			break;
		}
		return leftFinal;
	}

	/**
	 * 
	 * @param numJogador
	 * @param i
	 * @return Posição (y) da i-ésima carta na mão do jogador em questão
	 */
	private int getPosTopCarta(int numJogador, int i) {
		int deslocamentoVerticalEntreCartas = 4;
		int topFinal = 0;
		switch (numJogador) {
		case 1:
			topFinal = getHeight() - (CartaVisual.altura + MARGEM);
			break;
		case 2:
		case 4:
			topFinal = getHeight() / 2 - CartaVisual.altura / 2 - (i - 1)
					* deslocamentoVerticalEntreCartas;
			break;
		case 3:
			topFinal = MARGEM;
			break;
		}
		return topFinal;
	}

	/**
	 * Recolhe o vira e as cartas jogadas de volta para o baralho
	 */
	public void recolheMao() {
		cartas[0].visible = false;
		for (int i = 4; i <= 15; i++) {
			CartaVisual c = cartas[i];
			if ((c.top != topBaralho) || (c.left != leftBaralho)) {
				c.movePara(leftBaralho, topBaralho, 130);
				c.setCarta(null);
				c.descartada = false;
				c.escura = false;
				cartasJogadas.remove(c);
			}
		}
	}

	/**
	 * Joga a carta no meio da mesa
	 * 
	 * @param c
	 */
	public void descarta(Carta c, int posicao) {

		// Coloca a carta no meio da tela, mas "puxando" na direção
		// de quem jogou
		int topFinal = getPosTopDescartada(posicao);
		int leftFinal = getPosLeftDescartada(posicao);

		// Pega uma carta visual naquela posição...
		CartaVisual cv = null;
		for (int i = 0; i <= 2; i++) {
			CartaVisual cvCandidata = cartas[i + 1 + posicao * 3];
			// ...que não tenha sido descartada...
			if (cvCandidata.descartada) {
				continue;
			}
			// ...e, no caso de um humano (ou parceiro em mão de 11), que
			// corresponda à carta do jogo
			cv = cvCandidata;
			if (c.equals(cvCandidata)) {
				break;
			}
		}

		// Executa a animação de descarte
		cv.setCarta(c);
		cv.movePara(leftFinal, topFinal, 200);
		cv.descartada = true;
		cartasJogadas.addElement(cv);

	}

	/**
	 * 
	 * @param numJogador
	 * @return posição (x) de uma carta descartada pelo jogador (no meio da
	 *         tela, mas puxando para a direçãod dele com um breve distúrbio
	 *         aleatório)
	 */
	private int getPosLeftDescartada(int numJogador) {
		// Coloca a carta no meio da tela, mas "puxando" na direção
		// de quem jogou
		int leftFinal;
		leftFinal = getWidth() / 2 - CartaVisual.largura / 2;
		if (numJogador == 2) {
			leftFinal += CartaVisual.largura;
		} else if (numJogador == 4) {
			leftFinal -= CartaVisual.largura;
		}
		// Insere um ligeiro fator aleatório, para dar uma bagunçada na mesa
		leftFinal += System.currentTimeMillis() % 5 - 2;
		return leftFinal;
	}

	/**
	 * 
	 * @param numJogador
	 * @return posição (y) de uma carta descartada pelo jogador (no meio da
	 *         tela, mas puxando para a direçãod dele com um breve distúrbio
	 *         aleatório)
	 */
	private int getPosTopDescartada(int numJogador) {
		int topFinal;
		topFinal = getHeight() / 2 - CartaVisual.altura / 2;
		if (numJogador == 1) {
			topFinal += CartaVisual.altura / 2;
		} else if (numJogador == 3) {
			topFinal -= CartaVisual.altura / 2;
		}
		topFinal += System.currentTimeMillis() % 5 - 2;
		return topFinal;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		// Fundo verde
		canvas.drawRGB(27, 142, 60);

		// Desenha as cartas que já foram jogadas (se houverem),
		// na ordem em que foram jogadas
		for (int i = 0; i < cartasJogadas.size(); i++) {
			cartasJogadas.elementAt(i).draw(canvas);
		}

		// Desenha as cartas restantes, e o vira por cima de todas
		for (CartaVisual carta : cartas) {
			if (carta != null && !cartasJogadas.contains(carta)
					&& carta != cartas[0]) {
				carta.draw(canvas);
			}
		}
		cartas[0].draw(canvas);

		// Desliga o destaque da carta que fez a rodada e escurece as cartas já
		// descartadas (para não confundir com as próximas)
		long agora = System.currentTimeMillis();
		if (agora > rodadaPiscaAte && cartaQueFez != null
				&& cartaQueFez.destacada) {
			cartaQueFez.destacada = false;
			numRodadaPiscando = 0;
		}

		// Ícones das rodadas
		if (iconesRodadas != null) {
			for (int i = 0; i <= 2; i++) {
				// Desenha se não for a rodada piscando, ou, se for, alterna o
				// desenho a cada 250ms
				if (i != (numRodadaPiscando - 1) || (agora % 250) % 2 == 0) {
					canvas.drawBitmap(iconesRodadas[resultadoRodada[i]], MARGEM
							+ i * (2 + iconesRodadas[0].getWidth()),
							MARGEM + 1, new Paint());
				}
			}
		}

		// Caixa de diálogo (mão de 11 ou aumento)
		if (mostrarPerguntaMao11 || mostrarPerguntaAumento) {
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setColor(Color.BLACK);
			paint.setStyle(Style.FILL);
			canvas.drawRect(rectDialog, paint);
			paint.setColor(Color.WHITE);
			paint.setStyle(Style.STROKE);
			canvas.drawRect(rectDialog, paint);
			paint.setTextSize(tamanhoFonte);
			paint.setTextAlign(Align.CENTER);
			canvas.drawText(mostrarPerguntaMao11 ? "Aceita Mão de 11?"
					: "Aceita?", rectDialog.centerX(), rectDialog.top
					+ paint.getTextSize() * 1.2f, paint);
			desenhaBotao("Sim", canvas, rectBotaoSim);
			desenhaBotao("Nao", canvas, rectBotaoNao);

		}

		// Balãozinho (se alguém estiver falando algo)
		desenhaBalao(canvas);

	}

	private void desenhaBotao(String texto, Canvas canvas, RectF outerRect) {
		Paint paint = new Paint();
		paint.setStyle(Style.FILL);
		paint.setAntiAlias(true);
		paint.setTextSize(tamanhoFonte);
		// Borda
		paint.setColor(Color.WHITE);
		canvas.drawRoundRect(outerRect, tamanhoFonte * 2 / 3,
				tamanhoFonte * 2 / 3, paint);
		// Interior
		paint.setColor(Color.BLACK);
		RectF innerRect = new RectF(outerRect.left + 1, outerRect.top + 1,
				outerRect.right - 1, outerRect.bottom - 1);
		canvas.drawRoundRect(innerRect, tamanhoFonte * 2 / 3,
				tamanhoFonte * 2 / 3, paint);
		// Texto
		paint.setColor(Color.WHITE);
		paint.setTextAlign(Align.CENTER);
		canvas.drawText(texto, outerRect.centerX(), outerRect.centerY()
				+ tamanhoFonte * 0.3f, paint);
	}

	/**
	 * Cache dos ícones que informam o resultado das rodadas
	 */
	public static Bitmap[] iconesRodadas;

	/**
	 * Resultado das rodadas (0=não jogada; 1=vitória; 2=derrota; 3=empate)
	 */
	protected int[] resultadoRodada = { 0, 0, 0 };

	/**
	 * Margem entre a mesa e as cartas
	 */
	public static final int MARGEM = 2;

	/**
	 * Posição do baralho na mesa
	 */
	private int topBaralho, leftBaralho;

	/**
	 * Timestamp em que as animações em curso irão acabar
	 */
	private static long animandoAte = System.currentTimeMillis();

	/**
	 * Diz se é a vez do jogador humano dessa mesa (0=não, 1=sim, -1=sim mas
	 * está esperando resultado de truco.
	 * 
	 * TODO encapsular melhor isso.
	 */
	public static int vezHumano = 0;

	/**
	 * Jogo que a mesa está exibindo (deve ser setado externamente)
	 */
	public Jogo jogo;

	/**
	 * Guarda as cartas que foram jogadas pelos jogadores (para saber em que
	 * ordem desenhar)
	 */
	private Vector<CartaVisual> cartasJogadas = new Vector<CartaVisual>(12);

	private int numRodadaPiscando = 0;

	private long rodadaPiscaAte = System.currentTimeMillis();

	/**
	 * Carta que "fez" a última rodada (para fins de destaque)
	 */
	private CartaVisual cartaQueFez;

	public boolean mostrarPerguntaMao11 = false;

	private boolean recusarMao11 = false;
	private boolean aceitarMao11 = false;

	public boolean mostrarPerguntaAumento = false;

	private boolean recusarAumento = false;
	private boolean aceitarAumento = false;

	private int posicaoBalao = 1;

	private long mostraBalaoAte = System.currentTimeMillis();

	private String fraseBalao = null;

	/**
	 * Desenha a parte gráfica do balão (sem o texto). É chamada várias vezes
	 * para compor o contorno, antes de estampar o texto
	 * 
	 * @param canvas
	 *            onde ele será desenhado
	 * @param x
	 *            esquerda
	 * @param y
	 *            topo
	 * @param largBalao
	 *            largura
	 * @param altBalao
	 *            altura
	 * @param quadrantePonta
	 *            Quadrante (cartesiano) onde aparece a ponta do balão (com
	 *            relação a ele mesmo)
	 */
	private void desenhaElipseBalao(Canvas canvas, int x, int y, int largBalao,
			int altBalao, int quadrantePonta, Paint paint) {
		// Elipse principal
		paint.setAntiAlias(true);
		canvas.drawArc(new RectF(x, y, x + largBalao - 1, y + altBalao - 1), 0,
				360, false, paint);
		// Ponta (é um triângulo que desenhamos linha a linha)
		paint.setAntiAlias(false);
		int xi;
		for (int i = 0; i < altBalao; i++) {
			if (quadrantePonta == 2 || quadrantePonta == 3) {
				xi = x + altBalao * 3 / 2 - i;
			} else {
				xi = x - altBalao * 3 / 2 + i + largBalao;
			}
			int sinaly = quadrantePonta < 3 ? -1 : 1;
			canvas.drawLine(xi, y + altBalao / 2, xi, y + altBalao / 2 + i
					* sinaly, paint);
		}
	}

	/**
	 * Desenha o balão no lugar certo, se ele estiver visível
	 * 
	 * @param canvas
	 *            canvas onde ele será (ou não) desenhado.
	 */
	private void desenhaBalao(Canvas canvas) {
		if (fraseBalao != null && mostraBalaoAte > System.currentTimeMillis()) {

			// Determina o tamanho e a posição do balão e o quadrante da
			// ponta
			final int MARGEM_BALAO_LEFT = (int) tamanhoFonte;
			final int MARGEM_BALAO_TOP = (int) tamanhoFonte / 2;
			Paint paintFonte = new Paint();
			paintFonte.setAntiAlias(true);
			paintFonte.setTextSize(tamanhoFonte);
			Rect bounds = new Rect();
			paintFonte.setColor(Color.BLACK);
			paintFonte
					.getTextBounds(fraseBalao, 0, fraseBalao.length(), bounds);

			int largBalao = bounds.width() + 2 * MARGEM_BALAO_LEFT;
			int altBalao = bounds.height() + 2 * MARGEM_BALAO_TOP;
			int x = 0, y = 0;
			int quadrantePonta = 0;
			switch (posicaoBalao) {
			case 1:
				x = (canvas.getWidth() - largBalao) / 2 - CartaVisual.largura;
				y = canvas.getHeight() - altBalao - CartaVisual.altura - MARGEM
						- 3;
				quadrantePonta = 4;
				break;
			case 2:
				x = canvas.getWidth() - largBalao - MARGEM - 3;
				y = (canvas.getHeight() - altBalao) / 2;
				quadrantePonta = 1;
				break;
			case 3:
				x = (canvas.getWidth() - largBalao) / 2 + CartaVisual.largura;
				y = MARGEM + 3 + altBalao / 2;
				quadrantePonta = 2;
				break;
			case 4:
				x = MARGEM + 3;
				y = (canvas.getHeight() - altBalao) / 2 - CartaVisual.altura;
				quadrantePonta = 3;
				break;
			}

			// O balão tem que ser branco, com uma borda preta. Como
			// ele só aparece em um refresh, vamos pela força bruta,
			// desenhando ele deslocado em torno da posição final em
			// preto e em seguida desenhando ele em branco na posição
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setStyle(Paint.Style.FILL_AND_STROKE);
			paint.setColor(Color.BLACK);
			for (int i = -1; i <= 1; i++) {
				for (int j = -1; j <= 1; j++) {
					if (i != 0 && j != 0) {
						desenhaElipseBalao(canvas, x + i, y + j, largBalao,
								altBalao, quadrantePonta, paint);
					}
				}
			}
			paint.setColor(Color.WHITE);
			desenhaElipseBalao(canvas, x, y, largBalao, altBalao,
					quadrantePonta, paint);

			// Finalmente, escreve o texto do balão
			paint.setAntiAlias(true);
			canvas.drawText(fraseBalao, x + MARGEM_BALAO_LEFT, y + altBalao
					- MARGEM_BALAO_TOP - 2, paintFonte);

		} else {
			fraseBalao = null;
		}

	}

	public void aceitouAumentoAposta(Jogador j, int valor) {
		if (vezHumano == -1) {
			vezHumano = 1;
		}
	}

	private boolean visivel = false;

	public boolean vaiJogarFechada;

	public void setVisivel(boolean visivel) {
		this.visivel = visivel;
	}

}
